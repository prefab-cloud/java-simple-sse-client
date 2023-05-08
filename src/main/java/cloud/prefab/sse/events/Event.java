package cloud.prefab.sse.events;

public abstract class Event {

  enum Type {
    COMMENT,
    DATA,
  }

  abstract Type getType();
}
