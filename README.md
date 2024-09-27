# Lookup your hiccup

You wrote a function that returns hiccup, and now you want to test it.

But you don't care about all the bells and whistles,
only certain bits and pieces.

That's what `lookup` helps you do:
Find the parts that matter to you.

----

Lookup is a testing library for hiccup-like data, and it helps you:

- [Find interesting bits with CSS selectors](#find)
- [Normalize hiccup](#normalize)
- [Extract text content from hiccup](#extract-text)

## Install

With tools.deps:

```clj
no.cjohansen/lookup {:mvn/version "2024.09.xx"}
```

With Leiningen:

```clj
[no.cjohansen/lookup "2024.09.xx"]
```

<a id="find"></a>
## Find interesting bits with CSS selectors

A path is a vector of symbols (or strings) of CSS selectors. Like this:

- `'[a]` matches all anchor tags.
- `'[form input]` matches all input tags nested inside a form.
- `'[form > input]` matches all input tags that are direct children of a form.
- `'[div.foo]` matches all div tags with "foo" in its class name.
- `'[.button]` matches all elements with the "button" class.
- `'[div#content]` matches the div with "content" as its id.
- `'[:first-child]` matches any element that is the first child.
- `'[:last-child]` matches any element that is the last child.
- `'["meta[property]"]` matches all meta tags with the property attribute.
- `'["meta[property=og:title]"]` matches all meta tags with the property
  attribute set to "og:title".

Additionally supports all [attribute selector
operators](https://developer.mozilla.org/en-US/docs/Web/CSS/Attribute_selectors).

<a id="normalize"></a>
## Normalize hiccup

<a id="extract-text"></a>
## Extract text content
