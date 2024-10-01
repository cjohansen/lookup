# Lookup your hiccup

You wrote a function that returns hiccup, and now you want to test it.

But you don't care about all the bells and whistles,<br>
only certain bits and pieces.

That's what `lookup` helps you do:<br>
Find the parts that matter to you.

----

Lookup is a testing library for hiccup-like data, and it helps you:

- [Find interesting bits with CSS selectors](#find)
- [Normalize hiccup](#normalize)
- [Extract text content from hiccup](#extract-text)

## Install

With tools.deps:

```clj
no.cjohansen/lookup {:mvn/version "2024.10.01"}
```

With Leiningen:

```clj
[no.cjohansen/lookup "2024.10.01"]
```

<a id="find"></a>
## Find interesting bits with CSS selectors

`lookup.core/select` can be used to find hiccup nodes with a CSS selector. A
selector can be a single expression, like `'div` to find all divs, or a vector
representing a hierarchy, e.g. `'[div button.btn]` finds all `button` elements
with the class name `"btn"` that are descendants of a `div` element. The `>`
character can be used to specify direct children, e.g. `'[ul > li]` will only
match `li` elements with a direct `ul` parent.

```clj
(require '[lookup.core :as lookup])

(def hiccup
  [:div
   [:ul
    nil
    [:li "B1"]
    [:li.active
     [:a {:href "#"} "C"]]
    [:li "D1"]
    [:li "E"]]
   [:p "Paragraph 1"]
   [:h1 "Heading"]
   [:p "Paragraph 2"]])

(lookup/select '[ul > li a])
;;=> [[:a {:href "#"} "C"]]
```

Supported selector symbols:

- `'a` matches all anchor tags.
- `'[form input]` matches all input tags nested inside a form.
- `'[form > input]` matches all input tags that are direct children of a form.
- `'[h1 + p]` matches all paragraphs that are direct siblings of an h1.
- `'[h1 ~ p]` matches all paragraphs that are subsequent siblings of an h1.
- `'div.foo` matches all div tags with "foo" in its class name.
- `'.button` matches all elements with the "button" class.
- `'div#content` matches the div with "content" as its id.
- `':first-child` matches any element that is the first child.
- `':last-child` matches any element that is the last child.
- `'"meta[property]"` matches all meta tags with the property attribute.
- `'"meta[property=og:title]"` matches all meta tags with the property
  attribute set to "og:title".

Additionally supports all [attribute selector
operators](https://developer.mozilla.org/en-US/docs/Web/CSS/Attribute_selectors).

<a id="normalize"></a>
## Normalize hiccup

Hiccup is a flexible format, and when built with code it will often contain
noise such as nested lists of children, `nil`s, classes in several places, etc.
`lookup.core/normalize-hiccup` unifies all these things:

```clj
(require '[lookup.core :as lookup])

(lookup/normalize-hiccup
 [:div
  [:ul
   nil
   [:li {} "One"]
   [:li.active
    [:a {:href "#"} "Two"]]
   [:li "Three"]
   [:li "Four"]]
  [:p {:class "text-sm fg-red"} "Paragraph 1"]
  '([:h1 "Heading"]
   [:p "Paragraph 2"])])

;;=>
;; [:div {}
;;  [:ul {}
;;   [:li {} "One"]
;;   [:li {:class #{"active"}} [:a {:href "#"} "Two"]]
;;   [:li {} "Three"]
;;   [:li {} "Four"]]
;;  [:p {:class #{"fg-red" "text-sm"}}
;;   "Paragraph 1"]
;;  [:h1 {} "Heading"]
;;  [:p {} "Paragraph 2"]]
```

This form is ideal for further programmatic manipulation, as every node is
guaranteed to have an attribute map and a flat list of children. If you want
something that's better suited for human reading, employ `:strip-empty-attrs?`:

```clj
(require '[lookup.core :as lookup])

(lookup/normalize-hiccup
[:div
  [:ul
   nil
   [:li {} "One"]
   [:li.active
    [:a {:href "#"} "Two"]]
   [:li "Three"]
   [:li "Four"]]
  [:p {:class "text-sm fg-red"} "Paragraph 1"]
  '([:h1 "Heading"]
   [:p "Paragraph 2"])]
 {:strip-empty-attrs? true})

;;=>
;; [:div
;;  [:ul
;;   [:li "One"]
;;   [:li {:class #{"active"}}
;;    [:a {:href "#"} "Two"]]
;;   [:li "Three"]
;;   [:li "Four"]]
;;  [:p {:class #{"fg-red" "text-sm"}}
;;   "Paragraph 1"]
;;  [:h1 "Heading"]
;;  [:p "Paragraph 2"]]
```

To make assertions about the normalized hiccup, you can use `lookup.core/attrs`
and `lookup.core/children`, which both take a hiccup form and returns the
corresponding details:

```clj
(require '[lookup.core :as lookup])

(def hiccup
  [:p {:class "text-sm fg-red"}
   "Paragraph " [:strong "1"]])

(lookup/attrs hiccup)
;;=> {:class "text-sm fg-red"}

(lookup/children hiccup)
;;=> ("Paragraph " [:strong "1"])
```

<a id="extract-text"></a>
## Extract text content

`lookup.core/get-text` returns the text content of some hiccup:

```clj
(require '[lookup.core :as lookup])

(lookup/get-text [:h1 "Hello world"])
;;=> "Hello world"

(lookup/get-text
 [:div
  [:ul
   nil
   [:li {} "One"]
   [:li.active
    [:a {:href "#"} "Two"]]
   [:li "Three"]
   [:li "Four"]]
  [:p {:class "text-sm fg-red"} "Paragraph 1"]
  '([:h1 "Heading"]
    [:p "Paragraph 2"])])

;;=> "One Two Three Four Paragraph 1 Heading Paragraph 2"
```
