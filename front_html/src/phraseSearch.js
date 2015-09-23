class PhraseSearchInterface extends ReactSingleAjax {
    constructor(props) {
        super(props);

        let urlP = getURLParams();
        this.state = {
            leftWidth: parseInt(urlP.leftWidth) || 1,
            rightWidth: parseInt(urlP.rightWidth) || 1,
            termKind: urlP.termKind || "lemmas",
            pullSlices: urlP.pullSlices || true,
            scoreTerms: urlP.scoreTerms || true,
            findEntities: urlP.findEntities || true,
            numTerms: parseInt(urlP.numTerms) || 10,
            minTermFrequency: parseInt(urlP.minTermFrequency) || 10,
            numEntities: parseInt(urlP.numEntities) || 30,
            minEntityFrequency: parseInt(urlP.minEntityFrequency) || 4,
            query: urlP.query || "",
            count: parseInt(urlP.count) || 200
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
        request.numTerms = this.state.numTerms;
        request.minTermFrequency = this.state.minTermFrequency;
        if(this.state.pullSlices || this.state.pullSlices || this.state.findEntities) {
            request.pullSlices = true;
            request.leftWidth = this.state.leftWidth;
            request.rightWidth = this.state.rightWidth;
        }
        request.findEntities = this.state.findEntities;
        request.numEntities = this.state.numEntities;
        request.minEntityFrequency = this.state.minEntityFrequency;
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
        let findEntities = this.state.findEntities;

        return <UIWindow title="Phrase Search Interface">
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
                &nbsp;
                <label>
                    <input type="checkbox" checked={findEntities}
                           onChange={() => this.setState({findEntities: !this.state.findEntities})} />
                    Score Entitites
                </label>
            </div>
            <div>
                <IntegerInput visible={pullSlices || scoreTerms}
                              onChange={(x) => this.setState({leftWidth: x})}
                              min={0} max={20} start={this.state.leftWidth} label="Terms on Left: " />
                <IntegerInput visible={pullSlices || scoreTerms}
                              onChange={(x) => this.setState({rightWidth: x})}
                              min={0} max={20} start={this.state.rightWidth} label="Terms on Right: " />
                <IntegerInput visible={scoreTerms}
                              onChange={(x) => this.setState({numTerms: x})}
                              min={1} max={200} start={this.state.numTerms} label="Number of Terms to score: " />
                <IntegerInput visible={scoreTerms}
                              onChange={(x) => this.setState({minTermFrequency: x})}
                              min={0} max={20} start={this.state.minTermFrequency} label="Minimum Term Frequency: " />
                <IntegerInput visible={findEntities}
                              onChange={(x) => this.setState({numEntities: x})}
                              min={1} max={200} start={this.state.numEntities} label="Number of Entities to score: " />
                <IntegerInput visible={findEntities}
                              onChange={(x) => this.setState({minEntityFrequency: x})}
                              min={0} max={20} start={this.state.minEntityFrequency} label="Minimum Entity Frequency: " />
            </div>
            <Button label="Find!" onClick={(evt) => this.onFind(evt)}/>
            <PhraseSearchResultPanels error={this.state.error} request={this.state.request} response={this.state.response} />
        </UIWindow>;
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
        let rank = this.props.rank;

        let terms = _.map(x.terms || [], (term, idx) => {
            return [<StanfordNLPToken key={idx} index={idx} term={term} />, " "];
        });

        let divClasses = ["indent", "kwic"];
        if(_.isEmpty(terms)) {
            divClasses.push("none");
        }

        return <div>
            <span><strong>{rank}.</strong> <DocumentLink id={x.id} name={x.name}/></span>
            <div className={"indent kwic"}>{terms}</div>
        </div>
    }
}

class TermSearchResults extends React.Component {
    render() {
        let termResults = this.props.termResults;
        if(!termResults) {
            return <div>...</div>;
        }
        let setFilter = this.props.setFilter || ((x) => {});

        let pmiResults = _(termResults)
            .sortBy((x) => -x.pmi)
            .map((x, idx) => {
                let alt = JSON.stringify(x);
                //return <pre key={idx}>{JSON.stringify(x)}</pre>;
                return <tr title={alt} key={idx}>
                    <td onClick={(evt) => setFilter(x.docs)}>{x.term}</td>
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
    constructor(props) {
        super(props);
        this.state = {
            page: 0
        }
    }
    render() {
        let results = this.props.results;
        if(this.props.filterSet) {
            let keepFn = (x) => _.contains(this.props.filterSet, x);
            results = _.filter(results, keepFn);
        }
        let total = _.size(results);
        let shown = this.state.displayed;

        return <div>
            <PagedListView
                page={this.state.page}
                pageSize={10}
                items={results}
                renderItem={(x, idx) => {return <PhraseSearchResult rank={idx+1} result={x} />}}
                keyFn={result => result.id}
                updatePage={(pg) => this.setState({page: pg})}
                />
        </div>;
    }
}

class PhraseSearchResultPanels extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            filterSet: null
        }
    }
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

        let setFilter = (ids) => this.setState({filterSet: ids});
        return <div>
            <div>
                Found {resp.queryFrequency} results for <QueryDisplay text={req.query} kind={req.termKind} terms={resp.queryTerms} /> in {resp.time} milliseconds.
            </div>
            <div className="uiPanel">
                {(resp.termResults ? <UIWindow title="Word Cloud">
                    <WordCloud items={resp.termResults}
                               weightFn={(term) => {return term.pmi}}
                               termFn={(term) => { return term.term}} />
                </UIWindow> : '')}
                {(resp.termResults ? <UIWindow title="Term Result Table">
                    <TermSearchResults
                        setFilter={setFilter}
                        termResults={resp.termResults} />
                </UIWindow> : '')}
                {(resp.entities ? <UIWindow title="Entity Result Table">
                    <TermSearchResults
                        setFilter={setFilter}
                        termResults={resp.entities} />
                </UIWindow> : '')}
                <UIWindow title="Phrase Results">
                    <PhraseSearchResults
                        filterSet={this.state.filterSet}
                        results={resp.results} />
                </UIWindow>
            </div>
        </div>;
    }
}

class UIWindow extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            expanded: true
        };
    }
    render() {
        let contentStyles = [];
        contentStyles.push("uiWindowContent");
        if(!this.state.expanded) {
            contentStyles.push("none")
        }
        return <div className="uiWindow">
            <div className="uiWindowControls">
                {this.props.title}&nbsp;
            <input type="checkbox" checked={this.state.expanded}
                   onChange={() => this.setState({expanded: !this.state.expanded})}
                />
            </div>
            <div className={strjoin(contentStyles)}>
            {this.props.children}
            </div>
        </div>
    }
}

class WordCloud extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            applyLog: true,
            termFnSort: true
        }
    }
    render() {
        let weightFn = this.props.weightFn;
        let termFn = this.props.termFn;
        let items = _.sortBy(this.props.items,
            this.state.termFnSort ?
                termFn :
                (x) => { return -weightFn(x) });

        if(this.state.applyLog) {
            let oldWeightFn = weightFn;
            weightFn = (x) => { return Math.log(oldWeightFn(x)); };
        }

        let minWeight = _(items).map(weightFn).min();
        let maxWeight = _(items).map(weightFn).max();

        let minFontSize = 10.0;
        let maxFontSize = 30.0;

        let terms = _(items).map((x, idx) => {
            let weight = weightFn(x);
            let term = termFn(x);
            let relWeight = (weight - minWeight) / (maxWeight - minWeight);
            let fontSize = minFontSize + (maxFontSize - minFontSize)*relWeight;
            return [<span key={idx}
                          className={"WordCountTerm"}
                          style={{fontSize: fontSize}}>{x.term}</span>, " "];
        }).value();

        return <div>
            <label>
                Use Logarithmic Weights
                <input type="checkbox"
                       checked={this.state.applyLog}
                       onChange={() => this.setState({applyLog: !this.state.applyLog}) }
                    />
            </label>
            <Button
                disabled={this.state.termFnSort}
                onClick={() => this.setState({termFnSort: !this.state.termFnSort}) }
                label={"Term Sort"} />
            <Button
                disabled={!this.state.termFnSort}
                onClick={() => this.setState({termFnSort: !this.state.termFnSort}) }
                label={"Weight Sort"} />
            <hr />
            <div style={{lineHeight: maxFontSize+"pt"}}>{terms}</div>
            </div>
    }
}