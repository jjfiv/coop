var AjaxError = React.createClass({
    render: function() {
        var err = this.props.err;
        if(err.responseText) {
            return <textarea value={err.responseText}/>;
        }
        return <textarea value={JSON.stringify(this.props.err)}/>;
    }
});

var AjaxRequest = React.createClass({
    getInitialState: function() {
        return {
            request: null,
            response: null,
            waiting: false,
            error: null
        };
    },
    onSend: function(request) {
        this.setState({response: {}, request:request, waiting: true, error: null});
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
        // don't fire off equivalent requests
        if(_.isEqual(request, this.state.request)) {
            return;
        }
        this.onSend(request);
        postJSON(this.props.url, request, this.onSuccess, this.onError);
    },
    render: function() {
        if(!this.state.request) {
            return <span />;
        } else if(this.state.waiting) {
            if(this.props.quiet) {
                return <span />;
            }
            return <span className={"ajax ajaxWaiting"}>Waiting for server response.</span>;
        } else if(this.state.error != null) {
            return <AjaxError err={this.state.error} />;
        } else { //if(this.state.response) {
            if(this.props.quiet) {
                return <span />;
            }
            return <span className={"ajax ajaxComplete"}>{"Request completed in "+this.state.response.time+"ms."}</span>
        }
    }
});

