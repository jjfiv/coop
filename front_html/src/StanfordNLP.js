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

        let classes = ["token"];
        if(this.props.highlight) {
            classes.push("highlight");
        }

        if(_.startsWith(term, "____")) {
            return <hr id={anchor} />
        } else {
            return <span title={"#"+index} className={strjoin(classes)} id={anchor}>{term}</span>;
        }
    }

}