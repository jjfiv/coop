// The tiniest sync, async and debounced event bus library.

function createEventBus() {
    var mapped_events = {};
    var event_bus = {};

    event_bus.register= function(event_string, listenerFn) {
        var old_listeners = _.get(mapped_events, event_string, []);
        old_listeners.push(listenerFn);
        mapped_events[event_string] = old_listeners;
    };
    // Note, I'm not sure this function works at all.
    event_bus.unregister= function(event_string, listenerFn) {
        mapped_events[event_string] = _.without(mapped_events[event_string], listenerFn);
    };
    event_bus.signal= function(event_string, props) {
        _.forEach(_.get(mapped_events, event_string, []), function(listenerFn) {
            listenerFn(props, event_string);
        });
    };
    event_bus.async_signal= function (event_string, props) {
        _.defer(function() {
            signal(event_string, props);
        })
    };
    event_bus.debounced_async_signal= _.debounce(event_bus.async_signal.bind(event_bus), 50);
    return event_bus;
}

var EVENTS = createEventBus();
