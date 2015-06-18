

var SearchSentences = React.createClass({
    getInitialState: function() {
        return {
            requestCount: this.props.requestCount || 10,
            response: {},
            selected: null,
            waiting: true,
            error: null
        };
    },
    refreshData: function() {
        this.setState({response: {}, waiting: true, error: null});
        postJSON("/api/searchSentences", {
            count: this.state.requestCount
        }, function(data) {
            this.setState({response: data, error: null, waiting: false});
        }.bind(this), function (err) {
            this.setState({error: err, response: {}, waiting: false})
        }.bind(this))
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
        //this.refreshData();
    },
    render: function() {
        var components = [];
        var searchBar =
            <div>
                <input ref={"query"} type={"text"} placeholder={"Query"} />
                <input ref={"searchButton"} type={"button"} />
            </div>

        if(this.state.waiting) {
            return <div>Waiting for server response.</div>;
        } else if(this.state.error != null) {
            return <AjaxError err={this.state.error} />;
        } else {
            var sentences =
                <SentenceList selectedToken={this.state.selected} sentences={this.state.response.sentences} />;
            var footer = <div>
                {"Finding these "+ _.size(this.state.response.sentences)+ " sentences took "+this.state.response.time+"ms. "}
            </div>;


            if(this.state.selected) {
                return <div>{[sentences, footer, <TokenInfo token={this.state.selected} />]}</div>;
            } else {
                return <div>{[sentences, footer]}</div>;
            }
        }
    }
});
