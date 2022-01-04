package com.wgtwo.sample;

import com.wgtwo.api.v1.events.EventsProto.AckInfo;
import com.wgtwo.api.v1.subscription.SubscriptionEventServiceGrpc;
import com.wgtwo.api.v1.subscription.SubscriptionEventServiceGrpc.SubscriptionEventServiceStub;
import com.wgtwo.api.v1.subscription.SubscriptionEventsProto;
import com.wgtwo.sample.auth.BearerToken;
import io.grpc.Channel;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class EventListener {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private final ExecutorService callbackExecutor;
    private final Context.CancellableContext context = Context.current().fork().withCancellation();
    private final SubscriptionEventServiceStub stub;
    private final SubscriptionEventsProto.StreamHandsetChangeEventsRequest request;
    private final Callback callback;

    private EventListener(
            Channel channel,
            ExecutorService callbackExecutor,
            SubscriptionEventsProto.StreamHandsetChangeEventsRequest request,
            Supplier<String> accessTokenSupplier,
            Callback callback) {
        this.callbackExecutor = callbackExecutor;
        this.stub = createStub(channel, accessTokenSupplier);
        this.request = request;
        this.callback = callback;
    }

    public static EventListener createStarted(
            Channel channel,
            ExecutorService callbackExecutor,
            SubscriptionEventsProto.StreamHandsetChangeEventsRequest request,
            Supplier<String> accessTokenSupplier,
            Callback callback
    ) {
        var listener = new EventListener(channel, callbackExecutor, request, accessTokenSupplier, callback);
        listener.run();
        return listener;
    }

    public void close() {
        context.cancel(null);
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.interrupted();
        }
    }

    private void run() {
        System.out.println("STARTING NEW SUBSCRIPTION");
        // Running in a context to make it possible to cancel it
        context.run(() -> stub.streamHandsetChangeEvents(request, new EventObserver()));

    }

    public void acknowledge(AckInfo ackInfo) {
        var request =
                SubscriptionEventsProto.AckHandsetChangeEventRequest.newBuilder()
                        .setAckInfo(ackInfo)
                        .build();
        stub.ackHandsetChangeEvent(request, new StreamObserver<>() {
            @Override
            public void onNext(SubscriptionEventsProto.AckHandsetChangeEventResponse ackResponse) {
                System.out.println("Acknowledged event: " + ackInfo);
            }

            @Override
            public void onError(Throwable throwable) {
                System.err.println("Acknowledged event failed: " + ackInfo);
            }

            @Override
            public void onCompleted() {
            }
        });
    }

    private SubscriptionEventServiceStub createStub(Channel channel, Supplier<String> accessTokenSupplier) {
        var stub = SubscriptionEventServiceGrpc.newStub(channel);
        if (accessTokenSupplier != null) {
            stub = stub.withCallCredentials(new BearerToken(accessTokenSupplier));
        }
        return stub;
    }

    private class EventObserver implements StreamObserver<SubscriptionEventsProto.StreamHandsetChangeEventsResponse> {
        @Override
        public void onNext(SubscriptionEventsProto.StreamHandsetChangeEventsResponse subscribeEventsResponse) {
            var event = subscribeEventsResponse;
            callbackExecutor.submit(() -> {
                try {
                    callback.handle(event);
                    acknowledge(event.getMetadata().getAckInfo());
                } catch (Exception e) {
                    System.err.println("Exception while handling event - skipping ack");
                }
            });
        }

        @Override
        public void onError(Throwable t) {
            var code = getStatus(t);
            System.err.println("Code: " + code);
            if (code == Status.Code.UNAUTHENTICATED) {
                restart(10);
            } else {
                restart(1);
            }
        }

        @Override
        public void onCompleted() {
            System.out.println("Connection closed by server");
            restart(1);
        }

        private void restart(int waitSeconds) {
            if (!context.isCancelled()) {
                executor.schedule(() -> run(), waitSeconds, TimeUnit.SECONDS);
            }
        }

        private Status.Code getStatus(Throwable t) {
            if (t instanceof StatusRuntimeException) {
                return ((StatusRuntimeException) t).getStatus().getCode();
            }
            if (t instanceof StatusException) {
                return ((StatusException) t).getStatus().getCode();
            }
            return Status.Code.UNKNOWN;
        }
    }
}
