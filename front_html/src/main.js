class Main {
    static render(what) {
        React.render(what,document.getElementById("content"));
    }
    static index() {
        Main.render(<PhraseSearchInterface />);
    }
    static docs() {
        Main.render(<DocSearchInterface />);
    }
    static doc() {
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


