package com.wgtwo.sample;

import com.wgtwo.api.events.v0.EventsProto;

@FunctionalInterface
public interface Callback {
  void handle(EventsProto.Event event);
}
