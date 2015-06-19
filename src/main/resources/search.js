
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
            response: {},
            highlightNER: true,
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
    toggleNERHighlight: function() {
        this.setState({highlightNER: !this.state.highlightNER});
    },
    render: function() {
        var components = [
            <SearchBar searchCallback={this.handleSearch} />,
            <AjaxRequest ref={"ajax"} url={"/api/searchSentences"} onNewResponse={this.onSearchResults}  />
        ];

        if(this.state.response) {
            var response = this.state.response;
            var selected = this.state.selected;

            components.push(<label>Highlight NER Truth Data: <input type={"checkbox"} checked={this.state.highlightNER} onChange={this.toggleNERHighlight} /></label>);
            var sentences =
                <SentenceList highlightNER={this.state.highlightNER} queryTerms={response.queryTerms} selectedToken={selected} sentences={response.results} />;
            components.push(sentences);


            if(selected) {
                components.push(<TokenInfo token={selected} />);
            }
        }

        return <div>{components}</div>;
    }
});
