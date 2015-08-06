class DocSearchInterface extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            termKind: "lemmas",
            query: "the president",
            operation: "OR",
            request: null,
            response: null
        }
    }
    searching() {
        return this.state.request != null;
    }
    onFind(evt) {
        // one request at a time...
        if(this.searching()) return;

        let request = {};
        request.termKind = this.state.termKind;
        request.query = this.state.query;
        request.operation = this.state.operation;

        console.log(request);
        postJSON("/api/MatchDocuments",
            request,
            (data) => {this.setState({response: data})});

        // clear results:
        this.setState({
            request: request,
            response: null
        });
    }
    render() {
        let results = "";
        if(this.state.response) {
            results = <pre key="json">{JSON.stringify(this.state.response)}</pre>;
        }

        return <div>
            <div>Document Search</div>
            <textarea value={this.state.query} onChange={(x) => this.setState({query: x.target.value}) } />
            <SelectWidget opts={TermKindOpts} selected={this.state.termKind} onChange={(x) => this.setState({termKind: x})} />
            <SelectWidget opts={OperationKinds} selected={this.state.operation} onChange={(x) => this.setState({operation: x})} />

            <Button label="Find!" onClick={(evt) => this.onFind(evt)}/>
            <DocumentResults response={this.state.response} />
        </div>
    }
}

class DocumentResults extends React.Component {
    render() {
        let resp = this.props.response;
        if(resp == null) {
            return <span />;
        }

        let results = _(resp.results).map(function(obj) {
            return <li key={obj.id}><DocumentLink id={obj.id} name={obj.name} /></li>
        }).value();

        return <div>
            <label>Query Terms: <i>{strjoin(resp.queryTerms)}</i></label>
            <ul>{results}</ul>
        </div>;
    }
}

