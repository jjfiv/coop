var ClassifierList = React.createClass({
    getInitialState: function() {
        return {
            classifiers: null,
            time: 0
        }
    },
    getClassifierList: function(data) {
        this.setState({classifiers: data.classifiers, time: data.time})
    },
    componentDidMount: function() {
        this.refreshList();
    },
    refreshList: function() {
        this.refs.ajax.sendNewRequest({});
    },
    render: function() {
        var items = [];
        items.push(<span>{"Classifier List"}</span>);
        items.push(<input type={"button"} onClick={this.refreshList} value={"Refresh"} />);
        items.push(<AjaxRequest ref={"ajax"} url={"/api/listClassifiers"} onNewResponse={this.getClassifierList} />);
        if(this.state.classifiers) {
            items.push(<ul>{_(this.state.classifiers).map(function(info, name) {
                console.log(info);
                return <div>{name}</div>
            }, this).value()}</ul>);
        }

        return <div>{items}</div>;
    }
});