
var SearchBar = React.createClass({
    handleSubmit: function() {
        var domNode = React.findDOMNode(this.refs["query"]);
        this.props.searchCallback(domNode.value);
    },
    handleKeyPress: function(evt) {
        if(evt.which == 13) {
            this.handleSubmit();
        }
    },
    render: function() {
        return <div>
            <input onKeyPress={this.handleKeyPress} ref={"query"} type={"text"} placeholder={"Query"} />
            <input onClick={this.handleSubmit} type={"button"} value={"Go!"} />
        </div>;
    }
});

var AjaxRequest = React.createClass({
    getInitialState: function() {
        return {
            request: null,
            response: null,
            waiting: false,
            error: null
        };
    },
    onSend: function(request) {
        this.setState({response: {}, request:request, waiting: true, error: null});
    },
    onSuccess: function(data) {
        this.setState({response: data, error: null, waiting: false});
        this.props.onNewResponse(data);
    },
    onError: function (err) {
        this.setState({error: err, response: null, waiting: false});
        this.props.onNewResponse(null);
    },
    sendNewRequest: function(request) {
        // don't fire off equivalent requests
        if(_.isEqual(request, this.state.request)) {
            return;
        }
        this.onSend(request);
        postJSON(this.props.url, request, this.onSuccess, this.onError);
    },
    render: function() {
        if(!this.state.request) {
            return <div></div>;
        } else if(this.state.waiting) {
            return <div>Waiting for server response.</div>;
        } else if(this.state.error != null) {
            return <AjaxError err={this.state.error} />;
        } else { //if(this.state.response) {
            return <div>{"Request completed in "+this.state.response.time+"ms."}</div>
        }
    }
});

var SearchSentences = React.createClass({
    getInitialState: function() {
        return {
            requestCount: this.props.requestCount || 10,
            response: {},
            selected: null
        };
    },
    handleSignal: function(what, props) {
        if(what === 'clicked_token') {
            if (!this.state.selected || this.state.selected.tokenId !== props.tokenId) {
                this.setState({selected: props});
            } else {
                this.setState({selected: null});
            }
        }
    },
    componentDidMount: function() {
        EVENT_BUS.register('clicked_token', this);
    },
    handleSearch: function(txt) {
        txt = txt.trim();
        if(_.isEmpty(txt)) {
            return;
        }
        this.refs.ajax.sendNewRequest({
            count: this.state.requestCount,
            offset: 0,
            query: txt
        });
    },
    onSearchResults: function(response) {
        this.setState({response: response});
    },
    render: function() {
        var components = [
            <SearchBar searchCallback={this.handleSearch} />,
            <AjaxRequest ref={"ajax"} url={"/api/searchSentences"} onNewResponse={this.onSearchResults}  />
        ];

        if(this.state.response) {
            var response = this.state.response;
            var selected = this.state.selected;
            var sentences =
                <SentenceList queryTerms={response.queryTerms} selectedToken={selected} sentences={response.results} />;
            components.push(sentences);


            if(selected) {
                components.push(<TokenInfo token={selected} />);
            }
        }

        return <div>{components}</div>;
    }
});
