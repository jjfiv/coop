var RandomSentences = React.createClass({
    getInitialState: function() {
        return {
            requestCount: this.props.requestCount || 5,
            response: {},
            selected: null
        };
    },
    refreshData: function() {
        this.refs.ajax.sendNewRequest({
            count: this.state.requestCount
        });
        this.selected = null;
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
    onResults: function(data) {
        this.setState({response: data});
    },
    componentDidMount: function() {
        EVENT_BUS.register('clicked_token', this);
        this.refreshData();
    },
    render: function() {
        var components = [
            <AjaxRequest ref={"ajax"} url={"/api/randomSentences"} onNewResponse={this.onResults}  />
        ];
        if(this.state.response) {
            components.push(
                <SentenceList selectedToken={this.state.selected} sentences={this.state.response.sentences}/>);
        }

        if (this.state.selected) {
            components.push(<TokenInfo token={this.state.selected}/>);
        }

        return <div>{components}</div>;
    }
});

