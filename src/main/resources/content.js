var HomePage = React.createClass({
    render: function() {
        return <div>
            <strong>LabelMaker</strong> is a tool that helps you explore and label interesting pieces of data in text collections.
            </div>;
    }
});

var ClassifierInfo = React.createClass({
    render: function() {
        return <pre>{JSON.stringify(this.props.data, null, 2)}</pre>;
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
    render: function() {
        console.log(this.state.classifiers || {});
        var items = _(this.state.classifiers || {}).map(function(val, name) {
            console.log(val);
            return <ClassifierInfo data={val} />
        }).value();

        var setState = this.setState;
        return <div>
            <AjaxRequest
                ref={"listClassifiers"}
                quiet={true}
                pure={false}
                onNewResponse={this.onGetClassifiers}
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


