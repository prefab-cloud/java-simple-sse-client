# Java Simple SSE Client

This provides a bare-bones wrapper class (`SSEHandler`) that acts as a java (11+) Http Client LineSubscriber, converting incoming SSE data to an Event stream.

It Supports
* SSE Comment events
* SSE Data events, including the last known event id
* Propagation of normal or exceptional closure of the streaming connection

It currently omits the following

* Retry events from SSE Spec - these only provide a suggested retry time
* StreamHandler states - specify the state of the streaming connection, we didn't find them needed in our use
* Connection management/retries (we don't wrap the client so this is readily accessible


## Background

Prefab Uses Server Side Events (SSE) to push configuration updates to clients.
When we switched from GRPC streams for these updates, we looked around for some simple code to translate the SSE text stream into events and the results weren't straightforward.

### Alternatives

There are quite a few implementations but the leading contenders have heavyweight dependencies or daunting mountains of documentation.

* [Jersey - JAX/RS](https://eclipse-ee4j.github.io/jersey/) : required loads of dependencies in glassfish and Apache CXF, felt very "enterprisey"
* [Okhttp-sse](https://github.com/square/okhttp/tree/master/okhttp-sse) : requires okhttp and a bunch of kotlin runtime

Other implementations wrapped the HttpClient and SSL certificate management making them overly complex for our needs.

### Towards Building Our Own

Rolling our own implementation felt straightforward for the following reasons.

1) We're targeting Java 11+ and a couple of friends had mentioned that the new HttpClient was vastly improved over what used to be included in the box.
2) [The SSE spec](https://html.spec.whatwg.org/multipage/server-sent-events.html) is a quite accessible read, revealing a simple, plain-text and line-oriented protocol. 
3) [This post](https://adambien.blog/roller/abien/entry/receiving_server_sent_events_sses) showed me that the Java 11 client already had a line handler to do the messy work of compiling the stream of bytes to lines


## Getting Started


### Install

Add the maven dependency

```xml
<dependency>
    <groupId>cloud.prefab</groupId>
    <artifactId>sse-handler</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Usage

The `SSEHandler` is an implementation of `SubmissionPublisher` that'll propagate these events:
* onNext: Called with an `Event` instance that can be either a `DataEvent` or `CommentEvent`
* onError: when the upstream client is closed exceptionally
* onComplete: when the upstream client is closed


Here's an example code snippet

```java
 HttpRequest request = HttpRequest
        .newBuilder()
        .header("Accept", EVENT_STREAM_MEDIA_TYPE)
        .timeout(Duration.ofSeconds(5))
        .uri(URI.create("Your Url Here"))
        .build();


SSEHandler sseHandler = new SSEHandler()
sseHandler.subscribe([Your Event Listener]);

CompletableFuture<HttpResponse<Void>> future = httpClient.sendAsync(
        request,
        HttpResponse.BodyHandlers.fromLineSubscriber(sseHandler)
        );
```


Another example can be seen in the Prefab Java Client's [SseConfigStreamingSubscriber](https://github.com/prefab-cloud/prefab-cloud-java/blob/main/client/src/main/java/cloud/prefab/client/internal/SseConfigStreamingSubscriber.java) where another listener wraps SSE handle to further process the dataevents by Base64 decoding and Protobuf parsing before notifying the Prefab Client that new configurations have arrived


