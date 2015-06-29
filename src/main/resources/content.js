var HomePage = React.createClass({
    render: function() {
        return <div>
            <strong>LabelMaker</strong> is a tool that helps you explore and label interesting pieces of data in text collections.
            </div>;
    }
});

var KeyValueEditable = React.createClass({
    getInitialState: function() {
        return {
            editing: this.props.editByDefault,
            showEditButton: false
        };
    },
    startEditing: function() {
        this.setState({editing: true});
    },
    handleKey: function(evt) {
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
    onMouseOver: function() {
        if(!this.state.editing) { this.setState({showEditButton: true}); }
    },
    onMouseOut: function() {
        if(!this.state.editing) { this.setState({showEditButton: false}); }
    },
    render: function() {
        var valueElement = (!this.state.editing) ?
            <span>{(this.props.value || "NONE") +" "}<Button visible={this.state.showEditButton} onClick={this.startEditing} label={"Edit"} /></span> :
            <input ref={"newValue"} onKeyPress={this.handleKey} type={"text"} defaultValue={this.props.value || ""} />;

            return <label onMouseOver={this.onMouseOver} onMouseOut={this.onMouseOut} className={"edit"}>
                <span>{this.props.label + ": "}</span>
                {valueElement}</label>;
    }
});

var ClassifierInfo = React.createClass({
    onEditValue: function(key, value) {
        var request = {};
        request.classifier = this.props.data.id;
        request[key] = value;
        EVENTS.signal('updateClassifier', request);
    },
    render: function() {
        var data = this.props.data;
        var items = [];
        items.push(<KeyValueEditable onEditValue={this.onEditValue} propName={"name"} key={0} label={"Name"} value={data.name} />);
        items.push(<KeyValueEditable onEditValue={this.onEditValue} propName={"description"} key={1} label={"Description"} value={data.description} />);

        var total = data.positives + data.negatives;

        items.push(<span key="pos">{"Positives: "+data.positives+" Negatives: "+data.negatives+" Total: "+total}</span>);
        return <div className={"classifierInfo"}>{items}</div>;
    }
});

var LabelsPage = React.createClass({
    getInitialState: function() {
        return { classifiers: null }
    },
    componentDidMount: function() {
        EVENTS.register('classifiers', this.onGetClassifiers);
        EVENTS.register('classifier', this.onUpdateClassifiers);
    },
    onGetClassifiers: function(data) {
        this.setState({classifiers: data});
    },
    onUpdateClassifiers: function(data) {
        this.setState({
            classifiers: _(this.state.classifiers).map(function(old_info) {
                if(old_info.id == data.id) { return data; }
                return old_info;
            })
        });
    },
    render: function() {
        var items = _(this.state.classifiers || []).map(function(val) {
            return <ClassifierInfo key={val.id} data={val} />
        }, this).value();

        if(_.size(items) == 0) {
            items.push(<div key="notfound">No classifiers returned from server!</div>)
        }

        return <div>
            <div className={"classifiers"}>{items}</div>
        </div>

    }
});

var SearchResults = React.createClass({
    render: function() {
        var items = _.map(this.props.results, function(item, result_idx) {
            var terms = _.map(item, function(term, term_idx) {
                return <LabelingToken
                    key={term_idx} token={term} />
            });
            return <div key={result_idx}>{terms}</div>;
            //return <pre key={result_idx}>{JSON.stringify(item)}</pre>;
        });
        return <div>{items}</div>;
    }
});

var SearchResultsPage = React.createClass({
    getInitialState: function() {
        if(this.props.param.query) {
            EVENTS.async_signal('searchSentences', this.props.param);
        }
        return {waiting: false, response: null};
    },
    componentDidMount: function() {
        EVENTS.register("searchSentences", this.onStartSearch);
        EVENTS.register('searchSentencesResponse', this.onSentences);
    },
    onStartSearch: function() {
        this.setState({waiting: true});
    },
    onSentences: function(response) {
        this.setState({waiting: false, response: response});
    },
    render: function() {
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

var Content = React.createClass({
    getInitialState: function() {
        return this.props.defaultContent;
    },
    componentDidMount: function() {
        EVENTS.register('changeContent', this.onChangeContent);
        EVENTS.signal('changeContent', this.props.defaultContent);
    },
    onChangeContent: function(content) {
        pushURLParams(content);
        if(!_.isUndefined(content.query)) {
            EVENTS.signal('changeQuery', content.query);
        }
        this.setState(content);
        EVENTS.signal('changePage', this.getPage());
    },
    getPage: function() {
        return this.state.p || "home";
    },
    render: function() {
        var page = this.getPage();

        var pages = {
            home: <HomePage />,
            labels: <LabelsPage param={this.state} />,
            search: <SearchResultsPage param={this.state} />
        };

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


