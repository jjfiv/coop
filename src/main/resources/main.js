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
            {this.getTerm()}
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

var TokenInfo = React.createClass({
    render: function() {
        var pairs = _(this.props.token.terms)
            .map(function(v,k) {return {key: k, value: v}})
            /*
            .filter(function(pair) {
                if(pair.key.contains("overfit_")) return false;
                return true;
            })*/
            .value();

        pairs.push({key: "featureCount", value: _.size(this.props.token.indicators)});

        var dlitems = _(pairs)
            .map(function(pair) { return <tr><td>{pair.key}</td><td>{pair.value}</td></tr> })
            .value();

        return <table className="token-info">{dlitems}</table>;
    }
});

var AjaxError = React.createClass({
    render: function() {
        var err = this.props.err;
        if(err.responseText) {
            return <textarea value={err.responseText}/>;
        }
        return <textarea value={JSON.stringify(this.props.err)}/>;
    }
});