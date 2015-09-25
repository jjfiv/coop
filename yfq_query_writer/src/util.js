function strjoin(strs) {
    return _.reduce(strs, function (a, b) {
        return a + " " + b;
    });
}

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

function round(x, numDigits) {
    if(numDigits == 0) return Math.round(x);

    let shift = Math.pow(10, numDigits || 1);
    let tmp = Math.round(x * shift);
    return tmp / shift;
}
