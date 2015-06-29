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

var pushURLParams = function(params) {
    var urlParams = "?" + _(params).map(function(val, key) {
        //console.log(key + ":" + vals);
        return encodeURIComponent(key) + "=" + encodeURIComponent(val);
    }).join('&');

    // update URL without reloading!
    History.pushState(null, null, urlParams);
};

function strjoin(strs) {
    return _.reduce(strs, function (a, b) {
        return a + " " + b;
    });
}

/**
 * Usage: <Button visible={true,false} disabled={true,false} onClick={whatFn} label={text label} />
 */
var Button = React.createClass({
    getDefaultProps: function() {
        return {
            visible: true,
            disabled: false
        };
    },
    render: function() {
        return <input
            className={this.props.visible ? "normal" : "hidden"}
            disabled={this.props.disabled}
            type={"button"}
            title={this.props.title || this.props.label}
            onClick={this.props.onClick}
            value={this.props.label}
            />;
    }
});
