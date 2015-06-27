function postJSON(url, input_data, done_fn, err_fn) {
    if(!err_fn) {
        err_fn = standardErrorHandler;
    }
    var opts = {
        url: url,
        type: "POST",
        data: JSON.stringify(input_data),
        processData: false,
        contentType: "application/json",
        dataType: "json"
    };
    $.ajax(opts).done(done_fn).error(err_fn);
}

function standardErrorHandler(err) {
    console.error(err);
}

var AjaxError = React.createClass({
    propTypes: {
        retry: React.PropTypes.func
    },
    renderError: function() {
        var err = this.props.err;
        if(err.responseText) {
            return <textarea value={err.responseText}/>;
        }
        return <textarea value={JSON.stringify(this.props.err)}/>;
    },
    render: function() {
        var items = [];
        items.push(this.renderError());
        if(this.props.retry) {
            items.push(<Button onClick={this.props.retry} label={"Try Again"} />);
        }
        return <div>{items}</div>;
    }
});

var AjaxHelper = React.createMixin({
    propTypes: {
        onNewResponse: React.PropTypes.func.isRequired,
        url: React.PropTypes.string.isRequired
    },
    getInitialState: function() {
        return {
            request: null,
            response: null,
            waiting: false,
            error: null
        };
    },
    onSend: function(request) {
        this.setState({request: request, response: null, waiting: true, error: null});
    },
    onSuccess: function(data) {
        this.setState({response: data, error: null, waiting: false});
        this.props.onNewResponse(data);
    },
    onError: function (err) {
        this.setState({error: err, response: null, waiting: false});
        this.props.onNewResponse(null);
    },
    sendNewRequest: function(request) {
        if (this.props.pure && _.isEqual(request, this.state.request)) {
            return;
        }
        this.onSend(request);
        postJSON(this.props.url, request, this.onSuccess, this.onError);
    },
    onRetry: function() {
        sendNewRequest(this.state.request);
    },
    renderAjaxState: function(quiet) {
        if(this.state.error != null) {
            return <AjaxError err={this.state.error} retry={this.onRetry} />;
        }
        if(quiet) {
            return <span />;
        }
        if(this.state.response) {
            return <span className={"ajax ajaxComplete"}>{"Request completed in "+this.state.response.time+"ms."}</span>
        }
        if(this.state.waiting) {
            return <span className={"ajax ajaxWaiting"}>Waiting for server response.</span>;
        }

        return <span />;
    }
});

var AjaxOnMount = React.createMixin({
    propTypes: {
        initialArguments: React.PropTypes.object.isRequired
    },
    componentDidMount: function () {
        sendNewRequest(this.props.initialArguments)
    }
});

var AjaxRequest = React.createClass({
    mixins: [AjaxHelper],
    render: function() {
        return this.renderAjaxState(this.props.quiet);
    }
});

