var EVENT_BUS = createEventBus();

function postJSON(url, input_data, done_fn, err_fn) {
    if(!err_fn) {
        err_fn = standardErrorHandler;
    }
    var opts = {
        url: url,
        type: "POST",
        data: JSON.stringify(input_data),
        processData: false,
        contentType: "application/json",
        dataType: "json"
    };
    $.ajax(opts).done(done_fn).error(err_fn);
}

function standardErrorHandler(err) {
    console.error(err);
}

var Sentence = React.createClass({
    getSentenceId: function() {
        return this.props.tokens[0].sentenceId;
    },
    render: function() {
        var that = this;
        var listNodes = this.props.tokens.map(function(token) {
            return <Token selectedToken={that.props.selectedToken} token={token} />;
        });
        return <span className="sentence"><span className="sentenceId">{this.getSentenceId()}</span>{listNodes}</span>;
    }
});


var Token = React.createClass({
    handleClick: function() {
        EVENT_BUS.signal("clicked_token", this.getToken());
    },
    getToken: function() {
        return this.props.token;
    },
    computeTitle: function() {
        return _(this.getToken().terms).map(function(v, k) {
            if(k.contains("ner") && v == "O") return "";
            return k+"="+v;
        }).reject(_.isEmpty).join('; ')
    },
    getTerm: function() {
        // grab CoNNL-specific "true_terms"
        return this.getToken().terms.true_terms;
    },
    active: function() {
        if(!this.props.selectedToken) { return false; }
        return this.props.selectedToken.tokenId === this.getToken().tokenId;
    },
    render: function() {
        return <span onClick={this.handleClick}
                     className={this.active() ? "active-token" : "token"}
                     title={this.computeTitle()}>
            {this.getToken().tokenId +" "+this.getTerm()}
        </span>;
    }
});

var SentenceList = React.createClass({
    render: function() {
        var that = this;
        var sTags = _(this.props.sentences)
            .map(function(tokens) { return <li><Sentence selectedToken={that.props.selectedToken} tokens={tokens} /></li>; })
            .value();
        return <ul className="sentences">{sTags}</ul>;
    }
});

var RandomSentences = React.createClass({
    getInitialState: function() {
        return {
            requestCount: this.props.requestCount || 5,
            response: {},
            selected: null,
            waiting: true,
            error: null
        };
    },
    refreshData: function() {
        this.setState({response: {}, waiting: true, error: null});
        postJSON("/api/randomSentences", {
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
        this.refreshData();
    },
    render: function() {
        if(this.state.waiting) {
            return <div>Waiting for server response.</div>;
        } else if(this.state.error != null) {
            return <AjaxError err={this.state.error} />;
        } else {
            return <div>
                <SentenceList selectedToken={this.state.selected} sentences={this.state.response.sentences} />
                <hr />
                {"Finding these "+ _.size(this.state.response.sentences)+ " sentences took "+this.state.response.time+"ms. "}
            </div>;
        }
    }
});


$(function() {
    React.render(<RandomSentences requestCount={5} />, document.getElementById("sentences"));
});

