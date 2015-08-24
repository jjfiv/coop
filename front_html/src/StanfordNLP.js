class StanfordNLPToken {
    static normalize(x) {
        switch(x) {
            case "-LSB-": return "[";
            case "-RSB-": return "]";
            case "-LRB-": return "(";
            case "-RRB-": return ")";
            default: return x;
        }
    }
    render() {
        let index = this.props.index;
        let term = StanfordNLPToken.normalize(this.props.term);
        let anchor = "t"+index;

        if(_.startsWith(term, "____")) {
            return <hr id={anchor} />
        } else {
            return <span className="token" id={anchor}>{term}</span>;
        }
    }

}