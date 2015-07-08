var HomePage = React.createClass({
    render() {
        return <div>
            <strong>LabelMaker</strong> is a tool that helps you explore and label interesting pieces of data in text collections.
            </div>;
    }
});

var KeyValueEditable = React.createClass({
    getInitialState() {
        return {
            editing: this.props.editByDefault,
            showEditButton: false
        };
    },
    startEditing() {
        this.setState({editing: true});
    },
    handleKey(evt) {
        if(evt.which == 13) {
            this.setState({editing: false});

            var oldValue = this.props.value;
            var newValue = React.findDOMNode(this.refs.newValue).value.trim();
            if(_.isEmpty(newValue) || newValue === oldValue) {
                return;
            }

            this.props.onEditValue(this.props.propName, newValue);
        }
    },
    onMouseOver() {
        if(!this.state.editing) { this.setState({showEditButton: true}); }
    },
    onMouseOut() {
        if(!this.state.editing) { this.setState({showEditButton: false}); }
    },
    render() {
        var valueElement = (!this.state.editing) ?
            <span>{(this.props.value || "NONE") +" "}<Button visible={this.state.showEditButton} onClick={this.startEditing} label={"Edit"} /></span> :
            <input ref={"newValue"} onKeyPress={this.handleKey} type={"text"} defaultValue={this.props.value || ""} />;

            return <label onMouseOver={this.onMouseOver} onMouseOut={this.onMouseOut} className={"edit"}>
                <span>{this.props.label + ": "}</span>
                {valueElement}</label>;
    }
});

var SearchResultsPage = React.createClass({
    getInitialState() {
        if(this.props.param.query) {
            EVENTS.async_signal('searchSentences', this.props.param);
        }
        return {waiting: false, response: null};
    },
    componentDidMount() {
        EVENTS.register("searchSentences", this.onStartSearch);
        EVENTS.register('searchSentencesResponse', this.onSentences);
    },
    onStartSearch() {
        this.setState({waiting: true});
    },
    onSentences(response) {
        this.setState({waiting: false, response: response});
    },
    render() {
        if(this.state.response) {
            var response = this.state.response;
            return <div>
                <div>{"Found "+response.totalHits+" results in "+response.time+"ms for query "+ strjoin(response.queryTerms) +"."}</div>
                <SearchResults results={response.results} />
            </div>;
        } else {
            return <div>
                <pre>{JSON.stringify(this.props.param)}</pre>
            </div>;
        }
    }
});

var RankByClassifierPage = React.createClass({
    getInitialState() {
        return {
            classifierId: null,
            classifier: null,
            count: 100,
            timeLimit: 500,
            features: 100,
            response: null
        };
    },
    componentDidMount() {
        EVENTS.register('classifier', this.onUpdateClassifier);
        EVENTS.register('classifiers', this.onGetClassifiers);
        EVENTS.register('rankByClassifierResponse', this.onRankedTokens);
    },
    onUpdateClassifier(data) {
        if(data.id != this.state.classifierId) {
            return;
        }
        this.setState({classifier: data});
    },
    onGetClassifiers(data) {
        _.forEach(data, this.onUpdateClassifier);
    },
    onRankedTokens(data) {
        this.setState({response: data});
    },
    onChangeClassifier(id) {
        this.setState({classifierId: id, response: null});

        EVENTS.signal('listClassifiers');
        EVENTS.signal('rankByClassifier', {
            count: this.state.count,
            timeLimit: this.state.timeLimit,
            features: this.state.features,
            classifier: id
        });
    },
    componentWillReceiveProps(newProps) {
        if(newProps.param.p == 'labelResults' && newProps.param.id) {
            var newId = parseInt(newProps.param.id);
            if(this.state.classifierId != newId) {
                this.onChangeClassifier(newId);
            }
        }
    },
    render() {
        var items = [];


        var resp = this.state.response;
        if(!resp) {
            return <div>Waiting for label scoring...</div>;
        }

        var maxVisited = resp.totalTokens;
        if(resp.timeLimitExceeded) {
            maxVisited = resp.lastScored;
            var fraction = resp.lastScored/resp.totalTokens;
            var remaining = roundTo1DecimalPlace( ((1.0 - fraction) * resp.time) / fraction );
            items.push(<div key={0}>{"Scored "+percentString(fraction)+" of the collection in "+resp.time+"ms."}</div>);
            items.push(<div key={1}>{"It will probably take another "+remaining+"ms. to complete scoring."}</div>);
        }
        items.push(<div key={2}>{"Around "+percentString(resp.numScored / maxVisited)+" of the tokens so far have plausibly fit your label."}</div>);

        var highlightAll = _.map(resp.results, function(x) { return x.token.tokenId; });

        items.push(_(resp.results).map(function(x) {
            return x.token.sentenceId;
        }).unique().map(function(sid) {
            return <div key={"sl:"+sid}>
                <InternalLink
                    page="view" args={{id:sid}}
                    label={"S#"+sid} />
                <DocumentView key={"s:"+sid} id={sid} before={0} after={0} step={2} highlight={highlightAll} />;
            </div>;
            //return <DocumentView key={"s:"+sid} id={sid} before={0} after={0} step={2} highlight={highlightAll} />;
        }).value());

        /*items.push(_.map(resp.results, function(scoredToken) {
            var tok = scoredToken.token;
            var sid = tok.sentenceId;
            return <DocumentView key={sid} id={sid} before={0} after={0} step={2} highlight={highlightAll} />;
        }));*/


        return <div>{items}</div>;
    }
});

var Content = React.createClass({
    getInitialState() {
        return this.props.defaultContent;
    },
    componentDidMount() {
        History.Adapter.bind(window, 'statechange', function() {
            EVENTS.signal('changeContent', getURLParams());
        });
        EVENTS.register('changeContent', this.onChangeContent);
        EVENTS.signal('changeContent', this.props.defaultContent);
    },
    onChangeContent(content) {
        pushURLParams(content);
        if(!_.isUndefined(content.query)) {
            EVENTS.signal('changeQuery', content.query);
        }
        this.setState(content);
        EVENTS.signal('changePage', this.getPage());
    },
    getPage() {
        return this.state.p || "home";
    },
    render() {
        var page = this.getPage();

        var pages = {
            home: <HomePage />,
            labels: <LabelsPage param={this.state}/>,
            search: <SearchResultsPage param={this.state}/>,
            tags: <TagsAvailable param={this.state} />,
            labelResults: <RankByClassifierPage param={this.state} />
        };

        if (page == "view" && this.state.id) {
            pages.view = <DocumentView key="view" id={parseInt(this.state.id)} />;
        }

        var anyVisible = false;
        var items = _.map(pages, function(v,k) {
            var visible = (page === k);
            anyVisible |= visible;
            return <div key={k} className={(visible) ? "normal" : "none"}>{v}</div>;
        }, this);

        if(!anyVisible) {
            items.push(<div key={404}>{"No content for page \""+page+"\""}</div>)
        }
        return <div>{items}</div>;
    }
});

$(function() {
    React.render(<Content defaultContent={getURLParams()} />, document.getElementById("content"));
});


