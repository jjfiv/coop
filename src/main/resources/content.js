var HomePage = React.createClass({
    render: function() {
        return <div>
            <strong>LabelMaker</strong> is a tool that helps you explore and label interesting pieces of data in text collections.
            </div>;
    }
});

var KeyValueEditable = React.createClass({
    getInitialState: function() {
        return { editing: this.props.editByDefault };
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

            this.props.notifyNewValue(this.props.key, newValue);
        }
    },
    render: function() {
        var valueElement = (!this.state.editing) ?
            <span>{this.props.value || "NONE"}<Button onClick={this.startEditing} label={"Edit"} /></span> :
            <input ref={"newValue"} onKeyPress={this.handleKey} type={"text"} defaultValue={this.props.value || ""} />;

            return <label className={"edit"}>
                <span>{this.props.label + ": "}</span>
                {valueElement}</label>;
    }
});

var ClassifierInfo = React.createClass({
    render: function() {
        var data = this.props.data;
        var items = [];
        items.push(<KeyValueEditable key={"name"} label={"Name"} value={data.name} />);
        items.push(<KeyValueEditable key={"desc"} label={"Description"} value={data.description} />);

        var total = data.positives + data.negatives;;

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
        console.log("didMount");
        this.refs.listClassifiers.sendNewRequest({});
    },
    onGetClassifiers: function(data) {
        console.log("onGet: ");
        console.log(data);
        this.setState({classifiers: data.classifiers});
    },
    onUpdateClassifiers: function(data) {

    },
    render: function() {
        console.log(this.state.classifiers || {});
        var items = _(this.state.classifiers || {}).map(function(val, name) {
            return <ClassifierInfo key={name} data={val} />
        }).value();

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
                url={"/api/listClassifiers"} />
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


