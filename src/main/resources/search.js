
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

var SearchSentences = React.createClass({
    getInitialState: function() {
        return {
            requestCount: this.props.requestCount || 10,
            query: {},
            response: {},
            selected: null,
            waiting: false,
            error: null
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
        if(_.isEmpty(txt.trim())) {
            return;
        }
        if(this.state.query.text === txt) { // don't search the same thing again.
            return;
        }
        this.setState({response: {}, query:{text: txt}, waiting: true, error: null});
        postJSON("/api/searchSentences", {
            count: this.state.requestCount,
            offset: 0,
            query: txt
        }, function(data) {
            this.setState({response: data, error: null, waiting: false});
        }.bind(this), function (err) {
            this.setState({error: err, response: {}, waiting: false})
        }.bind(this))
    },
    render: function() {
        var components = [
            <SearchBar searchCallback={this.handleSearch.bind(this)} />
        ];

        if(this.state.waiting) {
            components.push(<div>Waiting for server response.</div>);
        } else if(this.state.error != null) {
            components.push(<AjaxError err={this.state.error} />);
        } else if(this.state.response.results) {
            var response = this.state.response;
            var selected = this.state.selected;

            var sentences =
                <SentenceList queryTerms={response.queryTerms} selectedToken={selected} sentences={response.results} />;
            var footer = <div>
                {"Finding these "+ _.size(response.results)+ " sentences took "+response.time+"ms. "}
            </div>;
            components.push(sentences, footer);


            if(selected) {
                components.push(<TokenInfo token={selected} />);
            }
        }

        return <div>{components}</div>;
    }
});
