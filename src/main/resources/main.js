var EVENT_BUS = createEventBus();

var Sentence = React.createClass({
    getSentenceId: function() {
        return this.props.tokens[0].sentenceId;
    },
    render: function() {
        var that = this;
        var listNodes = this.props.tokens.map(function(token) {
            return <Token selectedToken={this.props.selectedToken} highlightNER={this.props.highlightNER} token={token} />;
        }, this);

        if(this.props.displaySentenceId) {
            return <div className="sentence">
            <span className="sentenceId">{this.getSentenceId()}</span>
                {listNodes}
        </div>;
        }
        return <div className="sentence">{listNodes}</div>;
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
        var classes = [];
        var ner = this.getToken().terms.true_ner;
        var active = this.active();
        if(active) {
            classes.push("active-token");
        } else {
            classes.push("token");
        }
        if(!active && this.props.highlightNER && ner) {
            classes.push("ner-"+ner);
        }

        var classStr = _.reduce(classes, function(accum, x) {
            return accum + ' ' + x;
        });

        return <span onClick={this.handleClick}
                     className={classStr}
                     title={this.computeTitle()}>
            {this.getTerm()}
        </span>;
    }
});

var SentenceList = React.createClass({
    render: function() {
        var sTags = _(this.props.sentences)
            .map(function(tokens) {
                return <Sentence highlightNER={this.props.highlightNER} selectedToken={this.props.selectedToken} tokens={tokens} />;
            }, this)
            .value();
        return <div className="sentences">{sTags}</div>;
    }
});

var TokenInfo = React.createClass({
    render: function() {
        var pairs = _(this.props.token.terms)
            .map(function(v,k) {return {key: k, value: v}})
            .value();

        pairs.push({key: "featureCount", value: _.size(this.props.token.indicators)});

        var dlitems = _(pairs)
            .map(function(pair) { return <tr><td>{pair.key}</td><td>{pair.value}</td></tr> })
            .value();

        return <table className="token-info">{dlitems}</table>;
    }
});

var TabComponent = React.createClass({
    getInitialState: function() {
        return {
            activeTab: 0,
        }
    },
    select: function(index) {
        this.setState({activeTab: index});
    },
    selectByName: function(name) {
        _(this.props.children).findIndex(function(child) {
            return child.name === name;
        });
    },
    render: function() {
        var children = this.props.children;
        var tabButtons = _(children).map(function (child, idx) {
            var tabc = this;
            return <input type={"button"} value={child.name} onClick={function() { tabc.select(idx); }}/>;
        }, this).value();

        var properlyHiddenChildren = _(children).map(function(child, idx) {
            if(idx == this.state.activeTab) {
                return <div className={"activeTab"}>{child.content}</div>;
            } else {
                return <div className={"inactiveTab"}>{child.content}</div>;
            }
        }, this).value();

        return <div>
            <div>{tabButtons}</div><hr />
            {properlyHiddenChildren}
        </div>;
    }
});


