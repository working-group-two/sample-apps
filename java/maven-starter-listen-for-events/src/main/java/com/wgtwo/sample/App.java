package com.wgtwo.sample;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.wgtwo.api.v1.events.EventsProto;
import com.wgtwo.api.v1.subscription.SubscriptionEventsProto;
import com.wgtwo.api.v1.subscription.SubscriptionEventsProto.StreamHandsetChangeEventsRequest;
import com.wgtwo.sample.auth.ClientCredentialSource;
import com.wgtwo.sample.auth.WgTwoApi;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class App {

  public static void main(String[] args) {
    // Setup OAuth 2.0
    var clientID = System.getenv("WGTWO_CLIENT_ID");
    var clientSecret = System.getenv("WGTWO_CLIENT_SECRET");
    var service = new ServiceBuilder(clientID).apiSecret(clientSecret).build(WgTwoApi.instance());
    var scope = "events.roaming.subscribe events.handset_update.subscribe";
    var clientCredentialSource = ClientCredentialSource.of(service, scope);

    // Create gRPC channel (sandbox)
    var channel = ManagedChannelBuilder.forTarget("apisandbox.dub.prod.wgtwo.com").build();
    // Create gRPC channel (prod)
    // var channel = ManagedChannelBuilder.forTarget("api.wgtwo.com").build();

    // Executor for running callbacks concurrently
    var callbackExecutor = Executors.newFixedThreadPool(10);

    // Create request
    var request = StreamHandsetChangeEventsRequest.newBuilder()
            .setStreamConfiguration(EventsProto.StreamConfiguration.newBuilder()
                    .setMaxInFlight(50)
                    .build())
            .build();

    // Setup event listener that will automatically reconnect
    var eventListener = EventListener.createStarted(
            channel,
            callbackExecutor,
            request,
            clientCredentialSource::accessToken,
            App::handle
    );

    // Close channel when exiting
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      eventListener.close();
      shutdown(channel);
    }));

    // Block application from exiting
    try { Thread.currentThread().join(); } catch (InterruptedException ignored) { }
  }

  public static void handle(SubscriptionEventsProto.StreamHandsetChangeEventsResponse event) {
    System.out.println(event);
  }

  private static void shutdown(ManagedChannel channel) {
    try {
      System.out.println("Channel shutdown started");
      channel.shutdown().awaitTermination(10, TimeUnit.SECONDS);
      System.out.println("Channel shutdown complete");
    } catch (InterruptedException e) {
      Thread.interrupted();
    }
  }
}
