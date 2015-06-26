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
            updating: true,
            name: urlp.name,
            info: null,
        };
    },
    updateInfo: function(info) {
        this.setState({info: info});
    },
    componentDidMount: function() {
        this.refreshData(this.state.name);
    },
    refreshData: function() {
        this.setState({updating: true});
        this.refs.ajax.sendNewRequest({name: this.state.name});
    },
    render: function() {
        if(!this.state.name) { return <ClassifierList />; }

        var items = [];
        items.push(<AjaxRequest quiet={false} ref={"ajax"} url={"/api/listClassifiers"} onNewResponse={this.updateInfo} />);

        var info = this.state.info;
        if (info) {
            items.push(<label>{"Name: "}<span className={"fieldValue"}>{info.name}</span></label>);
            items.push(<label>{"Description: "}<span className={"fieldValue"}>{info.description || <i>NONE</i>}</span></label>);

            var totalCount = _.size(info.labelEvents);
            var positiveCount = _.size(_.filter(info.labelEvents, function(evt) { return evt.positive; }));
            var negativeCount = totalCount - positiveCount;

            items.push(<label>{"Positive Labels: "}<span className={"fieldValue"}>{positiveCount+"/"+totalCount}</span></label>);
            items.push(<label>{"Negative Labels: "}<span className={"fieldValue"}>{negativeCount+"/"+totalCount}</span></label>);

            items.push(<LabelRandomSentence classifier={this.state.name} refresh={this.refreshData} />);

            items.push(<RecentLabels labels={info.labelEvents} count={10} />);
            //items.push(<pre>{JSON.stringify(this.state.info)}</pre>);
        }

        return <div>{items}</div>;
    }
});

var RecentLabels = React.createClass({
    getInitialState: function() {
        return {tokensById: {}};
    },
    componentDidMount: function() {
        var tokenIds = _.map(this.props.labels, _.property("tokenId"));
        this.refs.ajaxTokens.sendNewRequest({
            tokens: tokenIds
        });
    },
    componentDidUpdate: function(props, state) {
        var alreadyLoaded = _.keys(this.state.tokensById);
        var tokenIds = _(this.props.labels)
            .map(_.property("tokenId"))
            .value();
        this.refs.ajaxTokens.sendNewRequest({
            tokens: tokenIds
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
        // TODO, group these by phrases first:
        // Now have an add to phrase event if times are much different?
        // How to group stream by event?
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
            return <tr className={evt.positive ? "positive" : "negative"}>
                <td>{evt.tokenId}</td>
                <td>{token ? token.terms.true_terms : "???"}</td>
                <td>{date.toLocaleDateString()+" at "+date.toLocaleTimeString()}</td>
            </tr>
        }).value();

        return <label> Recent Labels
        <table>
            <tr>
                <th>TokenId</th>
                <th>Token</th>
                <th>When</th>
            </tr>
            <tbody>
            {rows}
            </tbody>
        </table>
            <AjaxRequest quiet={true} pure={true} ref={"ajaxTokens"} url={"/api/pullTokens"} onNewResponse={this.receiveTokenInfo} />
        </label>;
    }
});


$(function() {


    React.render(<ClassifierMainView />, document.getElementById("classifierInfo"));
});
