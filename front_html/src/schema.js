class SchemaInterface extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            indexMeta: null
        };
    }
    componentDidMount() {
        Main.getIndexMeta(item => this.setState({indexMeta: item}))
    }
    render() {
        let meta = this.state.indexMeta;
        if(!meta) return <span>Loading...</span>;

        let schema = meta.schema || {};

        let schemaInfo = _(schema).map((val, varName) => {
            let type = val.type;
            switch(type) {
                case "categorical":
                    return <CategoricalVar key={varName} name={varName} info={val} />;
                case "number":
                    return <NumericalVar key={varName} name={varName} info={val} />;
                default:
                    return <pre>{JSON.stringify(val)}</pre>;
            }
        }).value();

        return <div className={"corpusInfo"}>
            <label>Words in Collection: {meta.collectionLength}</label>
            <label>Documents in Collection: {meta.documentCount}</label>
            <label>Tokenization: <code>{meta.tokenizer}</code></label>
            <div>{schemaInfo}</div>
            </div>;
    }
}

class SubmitQuery extends React.Component {
    render() {
        return <span
            title={JSON.stringify(this.props.query)}
            className={"query"}>{this.props.label}</span>;
    }
}

class CategoricalVar extends React.Component {
    render() {
        let info = this.props.info;
        let name = this.props.name;

        let itemRenderFn = (item, index) => {
            let qj = {
                kind: "varEquals",
                name: "name",
                value: item
            };
            return <SubmitQuery
                label={item}
                query={qj} />;
        };
        return <div className={"varInfo categorical"}>
            <label>{this.props.name}</label>
            <FilterableList renderItem={itemRenderFn} items={info.values} maxValues={10} />
        </div>
    }
}
class NumericalVar extends React.Component {
    render() {
        let info = this.props.info;
        return <div className={"varInfo numerical"}>
            <label>{this.props.name}</label>
            <p>Frequency: {info.frequency}</p>
            <p>Range: [{info.minValue},{info.maxValue}]</p>
        </div>
    }
}
