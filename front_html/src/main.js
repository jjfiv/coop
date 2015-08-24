class Navigation extends React.Component {
    render() {
        let pages = {
            "index.html": "Phrase Search",
            "docSearch.html": "Document Search",
            "schema.html": "About Corpus"
        };

        let links = _(pages).map((val, key) => {
            return <a className="nav" key={key} href={key}>{val}</a>;
        }).value();

        return <span>{links}</span>;
    }
}
Navigation.propTypes = {};

class Main {
    static indexMeta;

    static init() {
        React.render(<Navigation />,document.getElementById("header"));

        $.getJSON("/api/IndexMeta", {}, (data) => {
            console.log(data);
            Main.indexMeta = data;
        });
    }
    static getIndexMeta(callback) {
        if(Main.indexMeta != null) {
            callback(Main.indexMeta);
        } else {
            _.delay(Main.getIndexMeta, 20, callback);
        }
    }
    static render(what) {
        React.render(what,document.getElementById("content"));
    }
    static index() {
        Main.init();
        Main.render(<PhraseSearchInterface />);
    }
    static docs() {
        Main.init();
        Main.render(<DocSearchInterface/>);
    }
    static doc() {
        Main.init();
        Main.render(<DocViewInterface />);
    }
    static schema() {
        Main.init();
        Main.render(<SchemaInterface />);
    }
}

const TermKindOpts = {
    "pos": "Part of Speech",
    "lemmas": "Lemma or Stem",
    "tokens": "Tokens"
};

const OperationKinds = {
    "OR": "Any Terms (OR)",
    "AND": "All Terms (AND)"
};

class DocumentLink extends React.Component {
    render() {
        let id = this.props.id;
        let name = this.props.name;
        let loc = this.props.loc;
        let url = "/docView.html";
        url += "?id="+id;
        if(_.isNumber(loc)) {
            url += "&term" + loc;
            url += "#t" + loc;
        }
        return <a href={url}>{"#"+id+" "+name}</a>
    }
}
DocumentLink.propTypes = {
    id: React.PropTypes.number.isRequired,
    name: React.PropTypes.string.isRequired
};


