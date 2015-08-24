class DocViewInterface extends React.Component {
    constructor(props) {
        super(props);
        let urlP = getURLParams();
        this.state = {
            docId: parseInt(urlP.id) || 0,
            termId: parseInt(urlP.term) || -1,
            response: null
        }
    }
    componentDidMount() {
        postJSON("/api/PullDocument",
            {id: this.state.docId},
            (data) => this.setState({response: data}));
    }
    render() {
        let doc = this.state.response;
        if (doc == null) {
            return <div>Loading...</div>;
        }

        let tokens = _(doc.terms.tokens).map((x, idx) => {
            return [
                <StanfordNLPToken highlight={idx == this.state.termId} key={idx} index={idx} term={x}/>,
                " "];
        }).value();

        var sentences = _.map(doc.tags.sentence, (extent) => {
            var begin = extent[0];
            var end = extent[1];
            return <span className={"sentence"}>{
                _.slice(tokens, begin, end)
            }</span>;
        });

        return <div>
            <label>Document #{doc.identifier}: {doc.name}</label>
            <div>{sentences}</div>
        </div>
    }
}
