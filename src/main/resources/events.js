// The tiniest sync, async and debounced event bus library.

function createEventBus() {
    var mapped_events = {};
    var event_bus = {};

    event_bus.register= function(event_string, listener) {
            var old_listeners = _.get(mapped_events, event_string, []);
            old_listeners.push(listener);
            mapped_events[event_string] = old_listeners;
        };
    event_bus.unregister= function(event_string, listener) {
        mapped_events[event_string] = _.without(mapped_events[event_string], listener);
    };
    event_bus.signal= function(event_string, props) {
            _.forEach(_.get(mapped_events, event_string, []), function(x) {
                x.handleSignal(event_string, props);
            });
        };
    event_bus.async_signal= function (event_string, props) {
            _.defer(function() {
                signal(event_string, props);
            })
        };
    event_bus.debounced_async_signal= _.debounce(event_bus.async_signal.bind(event_bus), 50)
    return event_bus;
}