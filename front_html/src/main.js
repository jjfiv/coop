class Navigation extends React.Component {
    render() {
        let pages = {
            "index.html": "Phrase Search",
            "docSearch.html": "Document Search"
        };

        let links = _(pages).map((val, key) => {
            return <a className="nav" key={key} href={key}>{val}</a>;
        }).value();

        return <span>{links}</span>;
    }
}
Navigation.propTypes = {};

class Main {
    static navigation() {
        React.render(<Navigation />,document.getElementById("header"));
    }
    static render(what) {
        React.render(what,document.getElementById("content"));
    }
    static index() {
        Main.navigation();
        Main.render(<PhraseSearchInterface />);
    }
    static docs() {
        Main.navigation();
        Main.render(<DocSearchInterface />);
    }
    static doc() {
        Main.navigation();
        Main.render(<DocViewInterface urlParams={getURLParams()} />);
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
        return <a href={"/doc.html?id="+id}>{"#"+id+" "+name}</a>
    }
}
DocumentLink.propTypes = {
    id: React.PropTypes.number.isRequired,
    name: React.PropTypes.string.isRequired
};


