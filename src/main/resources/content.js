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
    propTypes: {
        updateFn: React.PropTypes.func.isRequired
    },
    onEditValue: function(key, value) {
        var request = {};
        request.classifier = this.props.data.id;
        request[key] = value;
        this.props.updateFn(request)
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
        return {
            classifiers: null
        }
    },
    componentDidMount: function() {
        this.refs["listClassifiers"].sendNewRequest({});
    },
    onGetClassifiers: function(data) {
        this.setState({classifiers: data.classifiers});
    },
    doUpdateClassifier: function(request) {
        this.refs["updateClassifier"].sendNewRequest(request);
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
            return <ClassifierInfo updateFn={this.doUpdateClassifier} key={val.id} data={val} />
        }, this).value();

        if(_.size(items) == 0) {
            items.push(<div key="notfound">No classifiers returned from server!</div>)
        }

        return <div>
            <AjaxRequest
                ref={"listClassifiers"}
                quiet={true}
                pure={false}
                onNewResponse={this.onGetClassifiers}
                url={"/api/listClassifiers"} />
            <AjaxRequest
                ref={"updateClassifier"}
                quiet={true}
                pure={false}
                onNewResponse={this.onUpdateClassifiers}
                url={"/api/updateClassifier"} />
        <div className={"classifiers"}>{items}</div>
        </div>

    }
});

var Content = React.createClass({
    render: function() {
        switch(this.props.page) {
            case "home": return <HomePage />;
            case "labels": return <LabelsPage param={this.props.param} />;

            default: return <div>{"No content for page \""+this.props.page+"\""}</div>;
        }
    }
});

$(function() {
    var param = getURLParams();
    var page = param.p || "home";

    React.render(<Content page={page} param={param} />, document.getElementById("content"));
});


