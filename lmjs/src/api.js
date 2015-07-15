function now() {
    return (new Date()).getTime();
}

class APIRequest {
    constructor(id, path, request, onDone, onErr) {
        this.id = id;
        this.path = path;
        this.request = request;
        this.sent = now();
        this.received = 0;
        this.onDone = onDone;
        this.onErr = onErr;
        this.error = null;
        this.response = null;
    }
    waiting() {
        return this.received <= 0;
    }
    success() {
        return waiting() || this.error == null;
    }
    data() {
        return this.response;
    }
    error() {
        return this.error;
    }
    handleDone(response) {
        this.response = response;
        this.received = now();
        if(this.onDone) {
            this.onDone(this.response);
        }
    }
    handleError(error) {
        this.error = error;
        this.received = now();
        if(this.onErr) {
            this.onErr(this);
        }
    }
}

var _API = null;
class APISystem {
    constructor() {
        this.requests = [];
        this.nextId = 0;
    }
    post(path, input, onDone, onErr) {
        if(!onErr) {
            onErr = standardErrorHandler;
        }
        let opts = {
            url: path,
            type: "POST",
            data: JSON.stringify(input),
            processData: false,
            contentType: "application/json",
            dataType: "json"
        };

        let req = new APIRequest(this.nextId++, path, input, onDone, onErr);
        this.requests.push(req);
        $.ajax(opts)
            .done(resp => req.handleDone(resp))
            .error(err => req.handleError(err));
    }
    listTags(callbackFn) {
        this.post("/api/listTags", {}, callbackFn);
    }
}
_API = new APISystem();


// Classifiers
var SingletonAPI = null;

class API extends React.Component {
    componentDidMount() {
        console.assert(SingletonAPI == null);
        SingletonAPI = this;
        EVENTS.register('updateClassifier', this.sendUpdate.bind(this));
        EVENTS.register('listClassifiers', this.sendList.bind(this));
        EVENTS.register('rankByClassifier', this.sendRankByClassifier.bind(this));

        EVENTS.register('searchSentences', this.sendSearchSentences.bind(this));
        EVENTS.register('pullSentences', this.sendPullSentences.bind(this));
        EVENTS.register('listTagsRequest', this.sendListTags.bind(this));
        EVENTS.register('createNewClassifier', this.sendCreateNewClassifier.bind(this));

        // Doesn't make sense for one person to request this since everyone wants it.
        EVENTS.signal('listClassifiers');
    }
    sendUpdate(request) {
        this.refs.update.sendNewRequest(request);
    }
    onUpdate(data) {
        EVENTS.signal('classifier', data);
    }
    sendList(request) {
        this.refs.list.sendNewRequest(request || {});
    }
    onList(data) {
        EVENTS.signal('classifiers', data.classifiers);
    }
    sendCreateNewClassifier(data) {
        if(data && data.name) {
            this.refs['createNewClassifier'].sendNewRequest(data);
        } else {
            throw new Error(data);
        }
    }
    onCreateNewClassifier(data) {
        // push as a data update
        EVENTS.signal('classifier', data);
        // and as a complete
        EVENTS.signal('createNewClassifierResponse', data);
    }
    sendListTags() {
        this.refs.listTags.sendNewRequest({});
    }
    onListTags(data) {
        EVENTS.signal('listTagsResponse', data.tags);
    }
    sendSearchSentences(request) {
        if(_.isEmpty(request.query.trim())) {
            // skip silly queries:
            return;
        }
        this.refs.search.sendNewRequest(request);
    }
    onSearchSentences(response) {
        EVENTS.signal('searchSentencesResponse', response);
    }
    sendPullSentences(request) {
        console.assert(_.isArray(request));
        this.refs.pullSentences.sendNewRequest({sentences: request});
    }
    onPullSentences(response) {
        EVENTS.signal('pullSentencesResponse', response.sentences);
    }
    sendRankByClassifier(request) {
        console.log('sendRankByClassifier: '+request);
        console.log(request);
        this.refs['rankByClassifier'].sendNewRequest(request);
    }
    onRankByClassifier(response) {
        console.log(response);
        EVENTS.signal('rankByClassifierResponse', response);
    }
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
}
