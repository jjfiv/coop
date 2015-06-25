var getURLParams = function() {
    var match,
        pl = /\+/g, // Regex for replacing addition symbol with a space
        search = /([^&=]+)=?([^&]*)/g,
        decode = function(s) {
            return decodeURIComponent(s.replace(pl, " "));
        },
        query = window.location.search.substring(1);

    var urlParams = {};
    while ((match = search.exec(query))) {
        var key = decode(match[1]);
        var value = decode(match[2]);
        if (value === "null") {
            value = null;
        } else if (value === "true") {
            value = true;
        } else if (value === "false") {
            value = false;
        }
        // it's possible there are multiple values for things such as labels
        if (_.isUndefined(urlParams[key])) {
            urlParams[key] = value;
        } else {
            // urlParams[key] += "&" + key + "=" + value;
            urlParams[key] += "," + value;
        }
    }
    return urlParams;
};

var ClassifierMainView = React.createClass({
    getInitialState: function() {
        var urlp = getURLParams();
        return {
            name: urlp.name
        };
    },
    updateInfo: function(info) {
        this.setState({info: info});
    },
    componentDidMount: function() {
        this.refreshData(this.state.name);
    },
    refreshData: function(name) {
        this.refs.ajax.sendNewRequest({name: name});
    },
    render: function() {
        if(!this.state.name) { return <ClassifierList />; }

        var items = [];
        items.push(<AjaxRequest ref={"ajax"} url={"/api/listClassifiers"} onNewResponse={this.updateInfo} />);

        if (this.state.info) {
            items.push(<pre>{JSON.stringify(this.state.info)}</pre>);
        }

        return <div>{items}</div>;
    }
});


$(function() {


    React.render(<ClassifierMainView />, document.getElementById("classifierInfo"));
});
