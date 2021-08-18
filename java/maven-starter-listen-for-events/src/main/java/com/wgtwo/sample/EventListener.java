package com.wgtwo.sample;

import com.wgtwo.api.events.v0.EventsProto;
import com.wgtwo.api.events.v0.EventsServiceGrpc;
import com.wgtwo.api.util.auth.BearerToken;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class EventListener {
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private final ExecutorService callbackExecutor;
    private final Context.CancellableContext context = Context.current().fork().withCancellation();
    private final EventsServiceGrpc.EventsServiceStub stub;
    private final EventsProto.SubscribeEventsRequest request;
    private final Callback callback;

    public EventListener(
            Channel channel,
            ExecutorService callbackExecutor,
            EventsProto.SubscribeEventsRequest request,
            Supplier<String> accessTokenSupplier,
            Callback callback) {
        this.callbackExecutor = callbackExecutor;
        this.stub = createStub(channel, accessTokenSupplier);
        this.request = request;
        this.callback = callback;
    }

    public void start() {
        if (started.getAndSet(true)) {
            return;
        }
        run();
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
        context.run(() -> stub.subscribe(request, new EventObserver()));

    }

    public void acknowledge(EventsProto.Event event) {
        var request =
                EventsProto.AckRequest.newBuilder()
                        .setSequence(event.getMetadata().getSequence())
                        .setInbox(event.getMetadata().getAckInbox())
                        .build();
        stub.ack(request, new StreamObserver<>() {
            @Override
            public void onNext(EventsProto.AckResponse ackResponse) {
                System.out.println("Acknowledged event: " + event.getMetadata().getSequence());
            }

            @Override
            public void onError(Throwable throwable) {
                System.err.println("Acknowledged event failed: " + event.getMetadata().getSequence());
            }

            @Override
            public void onCompleted() {}
        });
    }

    private EventsServiceGrpc.EventsServiceStub createStub(Channel channel, Supplier<String> accessTokenSupplier) {
        var stub = EventsServiceGrpc.newStub(channel);
        if (accessTokenSupplier != null) {
            stub = stub.withCallCredentials(new BearerToken(accessTokenSupplier));
        }
        return stub;
    }

    private class EventObserver implements StreamObserver<EventsProto.SubscribeEventsResponse> {
        @Override
        public void onNext(EventsProto.SubscribeEventsResponse subscribeEventsResponse) {
            var event = subscribeEventsResponse.getEvent();
            callbackExecutor.submit(() -> {
                try {
                    callback.handle(event);
                    acknowledge(event);
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
