// Classifiers
var SingletonClassifiers = null;
var Classifiers = React.createClass({
    componentDidMount: function() {
        console.assert(SingletonClassifiers == null);
        SingletonClassifiers = this;
        EVENTS.register('updateClassifier', this.sendUpdate);
        EVENTS.register('listClassifiers', this.sendList);

        // test that signals work:
        EVENTS.signal('listClassifiers');
    },
    sendUpdate: function(request) {
        console.log(request);
        this.refs.update.sendNewRequest(request);
    },
    onUpdate: function(data) {
        console.log(data);
        EVENTS.signal('classifier', data);
    },
    sendList: function(request) {
        console.log(request);
        this.refs.list.sendNewRequest(request || {});
    },
    onList: function(data) {
        console.log(data);
        EVENTS.signal('classifiers', data.classifiers);
    },
    render: function() {
        var quiet = true;
        return <div>
            <AjaxRequest
                ref={"list"}
                quiet={quiet}
                pure={false}
                onNewResponse={this.onList}
                url={"/api/listClassifiers"} />
            <AjaxRequest
                ref={"update"}
                quiet={quiet}
                pure={false}
                onNewResponse={this.onUpdate}
                url={"/api/updateClassifier"} />
            </div>;
    }
});
