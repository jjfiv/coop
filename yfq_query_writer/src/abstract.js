// This file is named so that it will be included before others.
// also, it includes abstract classes; which need to be defined first, or else React complains :(

class ReactSingleAjax extends React.Component {
    constructor(props) {
        super(props);
    }
    init() {
        return this.state = _.merge(this.state, {error: null, request: null, response: null});
    }
    send(target, msg) {
        console.log({url: target, request: msg});
        if(this.waiting()) return;
        this.setState({request: msg, response: null, error: null});
        postJSON(target, msg,
            (resp) => {
                console.log({url: target, req: msg, response: resp});
                this.setState({error: null, response: resp});
            },
            (err) => {
                console.error(err);
                this.setState({error: err, response: null});
            }
        )
    }
    waiting() {
        return this.state.request && this.state.response == null && this.state.error == null;
    }
    error() {
        return this.state.error;
    }
    errorMessage() {
        return error().responseText || JSON.stringify(error());
    }
    response() {
        return this.state.response;
    }
}

