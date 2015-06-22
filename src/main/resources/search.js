
var SearchBar = React.createClass({
    getInitialState: function() {
        return {
            requestCount: this.props.requestCount || 10
        }
    },
    handleSubmit: function() {
        var txt = React.findDOMNode(this.refs["query"]).value.trim();
        if(_.isEmpty(txt)) {
            return;
        }
        this.refs.ajax.sendNewRequest({
            count: this.state.requestCount,
            offset: 0,
            query: txt
        });
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
            <AjaxRequest ref={"ajax"} url={this.props.url} onNewResponse={this.props.searchCallback}  />
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
    onSearchResults: function(response) {
        this.setState({response: response});
    },
    toggleNERHighlight: function() {
        this.setState({highlightNER: !this.state.highlightNER});
    },
    render: function() {
        var components = [
            <SearchBar url={"/api/searchSentences"} searchCallback={this.onSearchResults} />
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
