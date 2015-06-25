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

        var info = this.state.info;
        if (info) {
            items.push(<div>{"Name: "}<span className={"fieldValue"}>{info.name}</span></div>);
            items.push(<div>{"Description: "}<span className={"fieldValue"}>{info.description || ""}</span></div>);


            items.push(<RecentLabels labels={info.labelEvents} count={10} />);
            items.push(<pre>{JSON.stringify(this.state.info)}</pre>);
        }

        return <div>{items}</div>;
    }
});

var RecentLabels = React.createClass({
    getInitialState: function() {
        return {tokensById: {}};
    },
    componentDidMount: function() {
        this.refs.ajaxTokens.sendNewRequest({
            tokens: _.map(this.props.labels, _.property("tokenId"))
        });
    },
    receiveTokenInfo: function(data) {
        var tokensById = { };
        _.forEach(data.tokens, function(tok) {
            tokensById[''+tok.tokenId] = tok;
        });
        this.setState({tokensById: tokensById});
    },
    render: function() {
        var recentFirst = _.sortBy(this.props.labels, function(evt) { return -evt.time; });
        var recentEvents = _(recentFirst)
            .filter(function(evt, index) { return index < this.props.count; }, this)
            .map(function (evt) {
                evt.date = new Date(evt.time);
                return evt;
            }).value();

        var tokensById = this.state.tokensById;

        var rows = _(recentEvents).map(function(evt) {
            var date = new Date(evt.time);
            var token = tokensById[''+evt.tokenId];
            console.log(token);
            return <tr>
                <td>{evt.tokenId}</td>
                <td>{token ? token.terms.true_terms : "???"}</td>
                <td>{evt.positive ? "POSITIVE" : "NEGATIVE"}</td>
                <td>{date.toLocaleDateString()+" at "+date.toLocaleTimeString()}</td>
            </tr>
        }).value();

        return <label> Recent Labels
        <table>
            <tr>
                <th>TokenId</th>
                <th>Token</th>
                <th>Label</th>
                <th>When</th>
            </tr>
            {rows}
        </table>
            <AjaxRequest ref={"ajaxTokens"} url={"/api/pullTokens"} onNewResponse={this.receiveTokenInfo} />
        </label>;
    }
});


$(function() {


    React.render(<ClassifierMainView />, document.getElementById("classifierInfo"));
});
