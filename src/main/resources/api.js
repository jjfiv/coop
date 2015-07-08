// Classifiers
var SingletonAPI = null;
var API = React.createClass({
    componentDidMount() {
        console.assert(SingletonAPI == null);
        SingletonAPI = this;
        EVENTS.register('updateClassifier', this.sendUpdate);
        EVENTS.register('listClassifiers', this.sendList);
        EVENTS.register('rankByClassifier', this.sendRankByClassifier);

        EVENTS.register('searchSentences', this.sendSearchSentences);
        EVENTS.register('pullSentences', this.sendPullSentences);
        EVENTS.register('listTagsRequest', this.sendListTags);
        EVENTS.register('createNewClassifier', this.sendCreateNewClassifier);

        // Doesn't make sense for one person to request this since everyone wants it.
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
    sendCreateNewClassifier(data) {
        if(data && data.name) {
            this.refs['createNewClassifier'].sendNewRequest(data);
        } else {
            throw new Error(data);
        }
    },
    onCreateNewClassifier(data) {
        // push as a data update
        EVENTS.signal('classifier', data);
        // and as a complete
        EVENTS.signal('createNewClassifierResponse', data);
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
    sendRankByClassifier(request) {
        console.log('sendRankByClassifier: '+request);
        console.log(request);
        this.refs['rankByClassifier'].sendNewRequest(request);
    },
    onRankByClassifier(response) {
        console.log(response);
        EVENTS.signal('rankByClassifierResponse', response);
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
                ref={"rankByClassifier"}
                quiet={quiet}
                pure={false}
                onNewResponse={this.onRankByClassifier}
                url={"/api/rankByClassifier"} />
            <AjaxRequest
                ref={"createNewClassifier"}
                quiet={quiet}
                pure={false}
                onNewResponse={this.onCreateNewClassifier}
                url={"/api/createNewClassifier"} />
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
