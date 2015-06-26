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
                items.push(<LabelingWidget tokens={this.state.sentence} name={this.props.classifier} />);
                items.push(<Button disabled={this.state.actionStarted} onClick={this.skipSentence} label={"Skip!"} />);
                items.push(<Button disabled={this.state.actionStarted} onClick={this.labelSentenceNegative} label={"No matching "+this.props.classifier+" here."} />)
            }
        }

        return <div>{items}</div>;
    }
});


var LabelingWidget = React.createClass({
    propTypes: {
        tokens: React.PropTypes.array.isRequired,
        name: React.PropTypes.string.isRequired
    },
    getInitialState: function() {
        return {
            startDragToken: null,
            hoverToken: null,
            endDragToken: null,
            positiveLabels: [],
        }
    },
    mouseDown: function() {
        return startDragToken != null;
    },
    handleMouse: function(tok, what, evt) {
        if(what === 'down') {
            this.setState({
                startDragToken: tok.tokenId,
                hoverToken: null,
                endDragToken: null
            });
        } else if(what === 'up') {
            if(this.state.startDragToken != null) {
                this.setState({
                    endDragToken: tok.tokenId,
                    hoverToken: null
                });
            }
        } else if(what === 'over') {
            if(this.state.startDragToken != null) {
                this.setState({hoverToken: tok.tokenId});
            }
        } else if(what === 'out') {
            if (this.state.startDragToken != null && this.state.hoverToken == tok.tokenId) {
                this.setState({hoverToken: null});
            }
        } else {
            console.log(what + " " + tok.tokenId);
        }
    },
    selectedTokens: function() {
        if(!this.state.startDragToken) {
            return [];
        }
        var start = this.state.startDragToken;
        var end = this.state.endDragToken || this.state.hoverToken || start;

        var pts = _.sortBy([start, end]); // they might select backwards
        return _.range(pts[0], pts[1]+1);
    },
    labelPositive: function() {
        this.setState({positiveLabels: _.union(this.state.positiveLabels, this.selectedTokens())});
        this.deselect();
    },
    deselect: function() {
        this.setState({
            startDragToken: null,
            hoverToken: null,
            endDragToken: null
        });
    },
    clearPositive: function() {
        this.setState({positiveLabels: []});
    },
    submitLabels: function() {
        var negative = _.difference(this.getAllIds(), this.state.positiveLabels);
        console.log({
                positive: this.state.positiveLabels,
                negative: negative
        });
    },
    getAllIds: function() {
        return _(this.props.tokens).map(_.property("tokenId")).value();
    },
    render: function() {
        var tokens = this.props.tokens;

        var tokenSet = this.selectedTokens();
        var tokElems = _(tokens)
            .map(function(tok) {
                var id = tok.tokenId;
                var active = _.contains(tokenSet, id);

                return <LabelingToken
                    token={tok}
                    handleMouse={this.handleMouse}
                    active={_.contains(tokenSet, id)}
                    positive={_.contains(this.state.positiveLabels, id)}
                    />
            }, this)
            .value();

        var buttons = [];
        if(!_.isEmpty(tokenSet)) {
            buttons.push(<Button label={"Deselect"} onClick={this.deselect} />);
            buttons.push(<Button label={"Label as "+this.props.name} onClick={this.labelPositive} />);
        }
        if(!_.isEmpty(this.state.positiveLabels)) {
            buttons.push(<Button label={"Clear Labels"} onClick={this.clearPositive} />);
            buttons.push(<Button label={"Submit Labels"} onClick={this.submitLabels} />);
        }

        return <div>
            {}
            <div>{tokElems}</div>
            {buttons}
        </div>;
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
        var positive = this.props.positive;

        classes.push("noselect");
        if(positive) {
            classes.push("positive-token");
        } else if(active) {
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
        return <span className={"noselect"} onClick={this.handleClick}
                     onMouseOver={mouseHandler("over")}
                     onMouseOut={mouseHandler("out")}
                     onMouseDown={mouseHandler("down")}
                     onMouseUp={mouseHandler("up")}
                     className={classStr}
                     title={this.computeTitle()}>
            {this.getTerm()}
        </span>;
    }
});
