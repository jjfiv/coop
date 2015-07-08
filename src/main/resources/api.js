// Classifiers
var SingletonAPI = null;
var API = React.createClass({
    componentDidMount() {
        console.assert(SingletonAPI == null);
        SingletonAPI = this;
        EVENTS.register('updateClassifier', this.sendUpdate);
        EVENTS.register('listClassifiers', this.sendList);
        EVENTS.register('searchSentences', this.sendSearchSentences);
        EVENTS.register('pullSentences', this.sendPullSentences);
        EVENTS.register('listTagsRequest', this.sendListTags);

        // test that signals work:
        EVENTS.signal('listClassifiers');
    },
    sendUpdate(request) {
        this.refs.update.sendNewRequest(request);
    },
    onUpdate(data) {
        EVENTS.signal('classifier', data);
    },
    sendList(request) {
        this.refs.list.sendNewRequest(request || {});
    },
    onList(data) {
        EVENTS.signal('classifiers', data.classifiers);
    },
    sendListTags() {
        this.refs.listTags.sendNewRequest({});
    },
    onListTags(data) {
        EVENTS.signal('listTagsResponse', data.tags);
    },
    sendSearchSentences(request) {
        if(_.isEmpty(request.query.trim())) {
            // skip silly queries:
            return;
        }
        this.refs.search.sendNewRequest(request);
    },
    onSearchSentences(response) {
        EVENTS.signal('searchSentencesResponse', response);
    },
    sendPullSentences(request) {
        console.assert(_.isArray(request));
        this.refs.pullSentences.sendNewRequest({sentences: request});
    },
    onPullSentences(response) {
        EVENTS.signal('pullSentencesResponse', response.sentences);
    },
    render() {
        var quiet = true;
        return <div>
            <AjaxRequest
                ref={"listTags"}
                quiet={quiet}
                pure={false}
                onNewResponse={this.onListTags}
                url={"/api/listTags"} />
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
