var HomePage = React.createClass({
    render: function() {
        return <div>
            <strong>LabelMaker</strong> is a tool that helps you explore and label interesting pieces of data in text collections.
            </div>;
    }
});

var LabelsPage = React.createClass({
    render: function() {
        return <div><b>Hello</b><pre>{JSON.stringify(this.props.param)}</pre></div>;
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


