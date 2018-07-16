# Multiple sessions

First form to session-1

```clj+session
---
{:session "session-1"
 :eval true}
---
> (+ 1 1)
```

Second form to session-2

```clj+session
---
{:session "session-2"
 :eval true}
---
> (+ 2 2)
```

So `*1*` in session-1 should be `2`

```clj+session
---
{:session "session-1"}
---
> *1
```

And `*1` in session-2 should be `4`

```clj+session
---
{:session "session-2"}
---
> *1
```
