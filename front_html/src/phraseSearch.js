class PhraseSearchInterface extends ReactSingleAjax {
    constructor(props) {
        super(props);

        let urlP = getURLParams();
        this.state = {
            leftWidth: parseInt(urlP.leftWidth) || 1,
            rightWidth: parseInt(urlP.rightWidth) || 1,
            termKind: urlP.termKind || "lemmas",
            pullSlices: urlP.pullSlices || false,
            scoreTerms: urlP.scoreTerms || true,
            query: urlP.query || "",
            count: parseInt(urlP.count) || 200,
        };
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
        if(this.state.pullSlices) {
            request.pullSlices = true;
            request.leftWidth = this.state.leftWidth;
            request.rightWidth = this.state.rightWidth;
        }
        request.scoreTerms = this.state.scoreTerms;

        if(_.isEmpty(request.query)) return;
        pushURLParams(request);
        this.send("/api/FindPhrase", request);
    }
    handleKey(evt) {
        // submit:
        if(evt.which == 13) { onFind(evt); }
    }
    render() {
        let pullSlices = this.state.pullSlices;
        let scoreTerms = this.state.scoreTerms;

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
                <label>
                <input type="checkbox"
                       checked={pullSlices}
                       onChange={() => this.setState({pullSlices: !this.state.pullSlices}) }
                    />
                    Show KWIC
                    </label>
                &nbsp;
                <label>
                    <input type="checkbox" checked={scoreTerms}
                           onChange={() => this.setState({scoreTerms: !this.state.scoreTerms})} />
                    Score Terms
                </label>
                </div>
                <div>
                &nbsp;
                <IntegerInput visible={pullSlices || scoreTerms}
                              onChange={(x) => this.setState({leftWidth: x})}
                              min={0} max={20} start={this.state.leftWidth} label="Terms on Left:" />
                <IntegerInput visible={pullSlices || scoreTerms}
                              onChange={(x) => this.setState({rightWidth: x})}
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

class PhraseSearchResult extends React.Component {
    render() {
        let x = this.props.result;

        if(!x.terms) {
            return <span>
            <DocumentLink id={x.id} name={x.name} loc={x.loc} />
                {JSON.stringify(x)}
        </span>
        } else {
            let terms = _.map(x.terms, (term, idx) => {
                return [<StanfordNLPToken key={idx} index={idx} term={term} />, " "];
            });
            return <span>
            <DocumentLink id={x.id} name={x.name}/>
                <div>{terms}</div>
        </span>
        }
    }
}

class TermSearchResults extends React.Component {
    render() {
        let termResults = this.props.termResults;
        if(!termResults) {
            return <div>...</div>;
        }

        let pmiResults = _(termResults)
            .sortBy((x) => -x.pmi)
            .map((x, idx) => {
                //return <pre key={idx}>{JSON.stringify(x)}</pre>;
                return <tr>
                    <td>{x.term}</td>
                    <td>{round(x.pmi, 2)}</td>
                    <td>{x.tf}</td>
                    <td>{x.qpf}</td>
                    </tr>;
            }).value();

        return <div><table>
            <tr>
                <th>Term</th>
                <th>PMI</th>
                <th>CF</th>
                <th>QF</th>
            </tr>
            {pmiResults}</table></div>;
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

        let docResults = _(resp.results).take(10).map((x, idx) => {
            return <li key={idx}><PhraseSearchResult result={x} /></li>
        }).value();

        let pmiResults = _(resp.termResults).map((x, idx) => {
            return <pre key={idx}>{JSON.stringify(x)}</pre>;
        }).value();


        return <div>
            <div className="phraseResults">
                <div>Found {resp.queryFrequency} results for <QueryDisplay text={req.query} kind={req.termKind} terms={resp.queryTerms} />.</div>
                <ul>{docResults}</ul>
            </div>
            <div className="termResults"><TermSearchResults termResults={resp.termResults} /></div>
        </div>;
    }
}

