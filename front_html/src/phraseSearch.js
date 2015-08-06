class PhraseSearchInterface extends ReactSingleAjax {
    constructor(props) {
        super(props);

        let urlP = getURLParams();
        this.state = {
            leftWidth: parseInt(urlP.leftWidth) || 1,
            rightWidth: parseInt(urlP.rightWidth) || 1,
            termKind: urlP.termKind || "lemmas",
            query: urlP.query || "",
            count: parseInt(urlP.count) || 200,
        }
        this.init();
    }
    componentDidMount() {
        if(this.state.query) {
            this.onFind(null);
        }
    }
    onFind(evt) {
        // one request at a time...
        if(this.waiting()) return;

        let request = {};
        request.termKind = this.state.termKind;
        request.count = this.state.count;
        request.query = this.state.query;

        if(_.isEmpty(request.query)) return;
        pushURLParams(request);
        this.send("/api/FindPhrase", request);
    }
    handleKey(evt) {
        // submit:
        if(evt.which == 13) { onFind(evt); }
    }
    render() {
        return <div>
            <div>Phrase Search Interface</div>
            <label>Query
                <input
                    type="text"
                    placeholder="Enter Phrase"
                    value={this.state.query}
                    onChange={(evt) => this.setState({query: evt.target.value})}
                    onKeyPress={(evt) => { if(evt.which == 13) this.onFind(evt); }}
                    /></label>

            <SelectWidget opts={TermKindOpts} selected={this.state.termKind} onChange={(x) => this.setState({termKind: x})} />
            <div>
                <IntegerInput onChange={(x) => this.setState({leftWidth: x})}
                              min={0} max={20} start={this.state.leftWidth} label="Terms on Left:" />
                <IntegerInput onChange={(x) => this.setState({rightWidth: x})}
                              min={0} max={20} start={this.state.rightWidth} label="Terms on Right:" />
            </div>
            <Button label="Find!" onClick={(evt) => this.onFind(evt)}/>
            <PhraseSearchResults error={this.state.error} request={this.state.request} response={this.state.response} />
        </div>;
    }
}

class QueryDisplay extends React.Component {
    render() {
        let text = this.props.text;
        let kind = this.props.kind;
        let terms = this.props.terms;

        let term_tags = _.map(terms, (term, idx) => <span key={idx} className="token">{term + " "}</span>);

        return <span>Query "{text}" [{TermKindOpts[kind]}] <span>{term_tags}</span></span>;
    }
}

class PhraseSearchResults extends React.Component {
    render() {
        let req = this.props.request;
        let resp = this.props.response;

        if(resp == null) {
            if(req != null) {
                return <div>Searching...</div>;
            }
            return <div></div>;
        }

        if(this.props.error) {
            return <div>{resp.responseText}</div>;
        }

        let results = _(resp.results).map((x, idx) => {
            return <li key={idx}><DocumentLink id={x.id} name={x.name} /> {JSON.stringify(x)}</li>
        }).value();

        return <div>
            <div>Found {resp.queryFrequency} results for <QueryDisplay text={req.query} kind={req.termKind} terms={resp.queryTerms} />.</div>
            <ul>{results}</ul>
        </div>;
    }
}

