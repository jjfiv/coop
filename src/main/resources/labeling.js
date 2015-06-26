var LabelRandomSentence = React.createClass({
    getInitialState: function() {
        return {
            started: false,
            actionStarted: false,
            sentence: null
        }
    },
    beginLabeling: function() {
        this.setState({started: true});
        this.refs.randomSentence.sendNewRequest({count: 1});
    },
    onSentence: function(sdata) {
        this.setState({sentence: sdata.sentences[0]});
    },
    labelSentenceNegative: function() {
        var events = _(this.state.sentence).map(function(token) {
            return {positive: false, time: (new Date()).getTime(), tokenId: token.tokenId };
        }).value();

        this.setState({actionStarted: true});
        var that = this;
        postJSON("/api/updateClassifier", {
            classifier: this.props.classifier,
            labels: events
        }, function (donedata) {
            that.finishPostingData();
        }, function (errdata) {
            throw new Error("AG!");
        })
    },
    finishPostingData: function() {
        this.props.refresh();
        this.setState({sentence: null, actionStarted: false});
        this.refs.randomSentence.sendNewRequest({count: 1});
    },
    skipSentence: function() {
        this.setState({sentence: null, actionStarted: false});
        this.refs.randomSentence.sendNewRequest({count: 1});
    },
    render: function() {
        var items = [];

        items.push(<AjaxRequest quiet={true} ref={"randomSentence"} url={"/api/randomSentences"} onNewResponse={this.onSentence} />);

        if(!this.state.started) {
            items.push(<div>Want to improve the accuracy? <input type={"button"} onClick={this.beginLabeling} value={"Label a random sentence"} />
            </div>);
        } else {
            items.push(<div>Labeling a random sentence: </div>);
            if(this.state.sentence) {
                //items.push(<Sentence tokens={this.state.sentence} />);
                items.push(<LabelingWidget tokens={this.state.sentence} />);
                items.push(<Button disabled={this.state.actionStarted} onClick={this.skipSentence} label={"Skip!"} />);
                items.push(<Button disabled={this.state.actionStarted} onClick={this.labelSentenceNegative} label={"No matching "+this.props.classifier+" here."} />)
            }
        }

        return <div>{items}</div>;
    }
});


var LabelingWidget = React.createClass({
    propTypes: {
        tokens: React.PropTypes.array.isRequired
    },
    getInitialState: function() {
        return {
            selectedTokens: [],
        }
    },
    handleMouse: function(tok, what, evt) {
        console.log(what+" "+tok.tokenId);
    },
    render: function() {
        var tokens = this.props.tokens;

        var tokElems = _(tokens)
            .map(function(tok) {
                return <LabelingToken token={tok} handleClick={this.clickToken} handleMouse={this.handleMouse} />
            }, this)
            .value();

        return <div>{tokElems}</div>;
    }
});

var LabelingToken = React.createClass({
    getDefaultProps: function() {
        return {
            highlightNER: false,
            active: false,
            hover: false,
            handleClick: function() { },
            handleMouse: function() { }
        }
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
    handleClick: function() {
        this.props.handleClick(this.getToken());
    },
    render: function() {
        var token = this.getToken();
        var classes = [];
        var ner = token.terms.true_ner;
        var active = this.props.active;
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

        var handleMouse = this.props.handleMouse;
        var mouseHandler = function(what) {
            return function(evt) {
                return handleMouse(token, what, evt);
            }
        };
        return <span onClick={this.handleClick}
                     onMouseOver={mouseHandler("over")}
                     onMouseOut={mouseHandler("out")}
                     onMouseDown={mouseHandler("down")}
                     onMouseUp={mouseHandler("down")}
                     className={classStr}
                     title={this.computeTitle()}>
            {this.getTerm()}
        </span>;
    }
});
