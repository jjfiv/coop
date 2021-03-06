function getURLParams() {
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
}

function makeURLFromParams(params) {
    return "?" + _(params).map(function(val, key) {
            //console.log(key + ":" + vals);
            return encodeURIComponent(key) + "=" + encodeURIComponent(val);
        }).join('&');
}
function pushURLParams(params) {
    History.pushState(null, null, makeURLFromParams(params));
}

function strjoin(strs) {
    return _.reduce(strs, function (a, b) {
        return a + " " + b;
    });
}

function roundTo1DecimalPlace(val) {
    return (Math.round(val * 10.0)) / 10.0;
}

function percentString(val) {
    return roundTo1DecimalPlace(val*100)+"%";
}

/**
 * Usage: <Button visible={true,false} disabled={true,false} onClick={whatFn} label={text label} />
 */
var Button = React.createClass({
    getDefaultProps() {
        return {
            visible: true,
            disabled: false
        };
    },
    render() {
        var classes = [];
        classes.push(this.props.visible ? "normal" : "hidden");
        return <input
            className={strjoin(classes)}
            disabled={this.props.disabled}
            type={"button"}
            title={this.props.title || this.props.label}
            onClick={this.props.onClick}
            value={this.props.label}
            />;
    }
});

var InternalLink = React.createClass({
    propTypes: {
        styles: React.PropTypes.arrayOf(React.PropTypes.string),
        label: React.PropTypes.string.isRequired,
        page: React.PropTypes.string.isRequired,
        args: React.PropTypes.object
    },
    getInitialState() {
        return {
            args: _.merge({}, {p: this.props.page}, this.props.args)
        }
    },
    handleClick(evt) {
        evt.preventDefault();
        evt.stopPropagation();
        EVENTS.signal('changeContent', this.state.args);
    },
    render() {
        return <a
            className={strjoin(this.props.styles)}
            href={makeURLFromParams(this.state.args)}
            onClick={this.handleClick}>{this.props.label}</a>
    }
});

var HelpButton = React.createClass({
    propTypes: {
        text: React.PropTypes.string.isRequired
    },
    getInitialState() {
        return {active: false};
    },
    handleClick(evt) {
        this.setState({active: !this.state.active});
    },
    render() {
        var text = this.props.text;
        var items = [];
        items.push(<Button key={"?"} title={text} onClick={this.handleClick} label={"?"} />);
        if(this.state.active) {
            items.push(<span key={"text"}>{" "+text}</span>);
        }
        return <div title={text}>{items}</div>;
    }
});