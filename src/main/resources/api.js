// Classifiers
var SingletonAPI = null;
var API = React.createClass({
    componentDidMount: function() {
        console.assert(SingletonAPI == null);
        SingletonAPI = this;
        EVENTS.register('updateClassifier', this.sendUpdate);
        EVENTS.register('listClassifiers', this.sendList);
        EVENTS.register('searchSentences', this.sendSearchSentences);
        EVENTS.register('pullSentences', this.sendPullSentences);

        // test that signals work:
        EVENTS.signal('listClassifiers');
    },
    sendUpdate: function(request) {
        this.refs.update.sendNewRequest(request);
    },
    onUpdate: function(data) {
        EVENTS.signal('classifier', data);
    },
    sendList: function(request) {
        this.refs.list.sendNewRequest(request || {});
    },
    onList: function(data) {
        EVENTS.signal('classifiers', data.classifiers);
    },
    sendSearchSentences: function(request) {
        if(_.isEmpty(request.query.trim())) {
            // skip silly queries:
            return;
        }
        this.refs.search.sendNewRequest(request);
    },
    onSearchSentences: function(response) {
        EVENTS.signal('searchSentencesResponse', response);
    },
    sendPullSentences: function(request) {
        console.assert(_.isArray(request));
        this.refs.pullSentences.sendNewRequest({sentences: request});
    },
    onPullSentences: function(response) {
        EVENTS.signal('pullSentencesResponse', response.sentences);
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
            <AjaxRequest
                ref={"search"}
                quiet={quiet}
                pure={true}
                onNewResponse={this.onSearchSentences}
                url={"/api/searchSentences"} />
            <AjaxRequest
                ref={"pullSentences"}
                quiet={quiet}
                pure={false}
                onNewResponse={this.onPullSentences}
                url={"/api/pullSentences"} />
            </div>;
    }
});
