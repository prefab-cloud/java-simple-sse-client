package cloud.prefab.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import cloud.prefab.sse.events.CommentEvent;
import cloud.prefab.sse.events.DataEvent;
import cloud.prefab.sse.events.Event;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SSEHandlerTest {

  SubmissionPublisher<String> submissionPublisher = new SubmissionPublisher<>();
  SSEHandler sseHandler = new SSEHandler();
  EndSubscriber endSubscriber = new EndSubscriber();

  @BeforeEach
  void setup() {
    submissionPublisher.subscribe(sseHandler);
    sseHandler.subscribe(endSubscriber);
  }

  @Test
  void itHandlesComments() {
    submissionPublisher.submit(":foobar\n");
    await()
      .atMost(3, TimeUnit.SECONDS)
      .untilAsserted(() ->
        assertThat(endSubscriber.events)
          .hasSize(1)
          .containsOnly(new CommentEvent("foobar"))
      );
  }

  @Test
  void itPublishesEventWithDefaultNameForData() throws InterruptedException {
    submissionPublisher.submit("data: hello\n");
    submissionPublisher.submit("\n");
    await()
      .atMost(3, TimeUnit.SECONDS)
      .untilAsserted(() ->
        assertThat(endSubscriber.events)
          .hasSize(1)
          .containsOnly(new DataEvent("message", "hello\n", ""))
      );
  }

  @Test
  void itPublishesEventWithGivenNameForData() throws InterruptedException {
    submissionPublisher.submit("event: coolEvent");
    submissionPublisher.submit("data: hello\n");
    submissionPublisher.submit("\n");
    await()
      .atMost(3, TimeUnit.SECONDS)
      .untilAsserted(() ->
        assertThat(endSubscriber.events)
          .hasSize(1)
          .containsOnly(new DataEvent("coolEvent", "hello\n", ""))
      );
  }

  @Test
  void itPublishesEventWithGivenNameForDataAndLastEventId() throws InterruptedException {
    submissionPublisher.submit("event: coolEvent");
    submissionPublisher.submit("id: 101A");
    submissionPublisher.submit("data: hello\n");
    submissionPublisher.submit("\n");
    await()
      .atMost(3, TimeUnit.SECONDS)
      .untilAsserted(() ->
        assertThat(endSubscriber.events)
          .hasSize(1)
          .containsOnly(new DataEvent("coolEvent", "hello\n", "101A"))
      );
  }

  @Test
  void itPublishesEventWithGivenNameForMultiLineDataAndLastEventId()
    throws InterruptedException {
    submissionPublisher.submit("event: coolEvent");
    submissionPublisher.submit("id: 101A");
    submissionPublisher.submit("data: hello\n");
    submissionPublisher.submit("data: world\n");
    submissionPublisher.submit("data: !\n");
    submissionPublisher.submit("\n");
    await()
      .atMost(3, TimeUnit.SECONDS)
      .untilAsserted(() ->
        assertThat(endSubscriber.events)
          .hasSize(1)
          .containsOnly(new DataEvent("coolEvent", "hello\nworld\n!\n", "101A"))
      );
  }

  @Test
  void itPublishesEventWithGivenNameForDataAndIgnoresNullEventId()
    throws InterruptedException {
    submissionPublisher.submit("event: coolEvent");
    submissionPublisher.submit("id: \0");
    submissionPublisher.submit("data: hello\n");
    submissionPublisher.submit("\n");
    await()
      .atMost(3, TimeUnit.SECONDS)
      .untilAsserted(() ->
        assertThat(endSubscriber.events)
          .hasSize(1)
          .containsOnly(new DataEvent("coolEvent", "hello\n", ""))
      );
  }

  @Test
  void itPropagatesClose() throws InterruptedException {
    submissionPublisher.close();
    await()
      .atMost(3, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(endSubscriber.isComplete.get()).isTrue());
  }

  @Test
  void itPropagatesError() throws InterruptedException {
    Exception e = new RuntimeException("closing exceptionally!");
    submissionPublisher.closeExceptionally(e);
    await()
      .atMost(3, TimeUnit.SECONDS)
      .untilAsserted(() ->
        assertThat(endSubscriber.throwableAtomicReference.get()).isEqualTo(e)
      );
  }

  @Test
  void itDoesNotSendDataEventWithoutDataBufferContents() throws InterruptedException {
    submissionPublisher.submit("\n");
    submissionPublisher.close();
    await()
      .atMost(3, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        assertThat(endSubscriber.isComplete.get())
          .isTrue();
        assertThat(endSubscriber.events).isEmpty();
        }

      );
  }

  private static class EndSubscriber implements Flow.Subscriber<Event> {

    private static final Logger LOG = LoggerFactory.getLogger(EndSubscriber.class);

    private Flow.Subscription subscription;

    private final CopyOnWriteArrayList<Event> events = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isComplete = new AtomicBoolean(false);
    private final AtomicReference<Throwable> throwableAtomicReference = new AtomicReference<>();

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
      this.subscription.request(1);
    }

    @Override
    public void onNext(Event item) {
      events.add(item);
      LOG.info("Received event {}, Now have {} events", item, events.size());
      this.subscription.request(1);
    }

    @Override
    public void onError(Throwable throwable) {
      throwableAtomicReference.set(throwable);
    }

    @Override
    public void onComplete() {
      isComplete.set(true);
    }
  }
}
