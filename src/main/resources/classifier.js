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
    labelBest: function(name) {
        return function() {
            console.log("Find best! "+name);
        };
    },
    labelRandom: function(name) {
        return function() {
            console.log("Find best! "+name);
        };
    },
    render: function() {
        var items = [];
        items.push(<span>{"Classifier List"}</span>);
        items.push(<input type={"button"} onClick={this.refreshList} value={"Refresh"} />);
        items.push(<AjaxRequest quiet={true} ref={"ajax"} url={"/api/listClassifiers"} onNewResponse={this.getClassifierList} />);

        var perClassifierButtons = [];
        perClassifierButtons.push(
            <input type={"button"} onClick={this.labelBest(name)} value={"Label Best"} />,
            <input type={"button"} onClick={this.labelRandom(name)} value={"Label Random"} />,
            <a href={"/classifier.html?name="+name}>{"View"}</a>
        );

        if(this.state.classifiers) {
            var rows = _(this.state.classifiers).map(function(info, name) {
                var row = [];
                row.push(<td>{name}</td>);
                row.push(<td>{_.size(info.positive)}</td>);
                row.push(<td>{_.size(info.negative)}</td>);
                row.push(<td>{perClassifierButtons}</td>);
                return <tr>{row}</tr>;
            }, this).value();
            return <table>
                <tr><th>{"Classifier Name"}</th>
                    <th>{"Positive Labels"}</th>
                    <th>{"Negative Labels"}</th>
                    <th>{"Actions"}</th></tr>
                {rows}
            </table>;
        }

        return <div>{items}</div>;
    }
});