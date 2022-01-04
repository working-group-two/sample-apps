package com.wgtwo.sample;

import com.wgtwo.api.v1.subscription.SubscriptionEventsProto;

@FunctionalInterface
public interface Callback {
    void handle(SubscriptionEventsProto.StreamHandsetChangeEventsResponse event);
}
