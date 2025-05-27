# Fulcro Developer Guide - AI Summary

## Table of Contents

1. **[About This Book](01-about-this-book.md)**
   - Overview of Fulcro 3.x guide
   - Resources and community
   - React compatibility
   - Development tools setup

2. **[Fulcro Overview](02-fulcro-overview.md)**
   - Core philosophy and architecture
   - Graph-centric design principles
   - Client database normalization
   - Component-driven development
   - Operational model with mutations

3. **[Core Concepts](03-core-concepts.md)**
   - Immutable data structures benefits
   - Pure rendering model
   - Data-driven architecture
   - Graph database structure
   - Idents and relationships

4. **[Getting Started](04-getting-started.md)**
   - Development environment setup
   - Project structure and configuration
   - Shadow-cljs build tool
   - Basic component creation
   - Hot code reload

5. **[Transactions and Mutations](05-transactions-and-mutations.md)**
   - Transaction system overview
   - Mutation definition and structure
   - Normalized database operations
   - Full-stack mutation handling
   - Merge helpers and utilities

6. **[Data Loading](06-data-loading.md)**
   - `load!` function patterns
   - Server setup with Pathom
   - Targeting and parameters
   - Loading indicators and error handling
   - Advanced loading scenarios

7. **[Core API](07-core-api.md)**
   - Denormalization with `db->tree`
   - Normalization with `tree->db`
   - Initial state composition
   - Rendering pipeline
   - Graph evolution operations

8. **[Components and Rendering](08-components-and-rendering.md)**
   - HTML5 element factories
   - `defsc` macro features
   - Component options and lifecycle
   - Factory functions
   - React integration patterns

9. **[Server Interactions](09-server-interactions.md)**
   - EQL over Transit protocol
   - Pathom resolver patterns
   - Server mutation handling
   - Error handling and security
   - Development and debugging tools

10. **[UI State Machines](10-ui-state-machines.md)**
    - State machine concepts and benefits
    - Machine definition and structure
    - Actor model and event handling
    - Remote operations and workflows
    - Common patterns and best practices

11. **[Dynamic Routing](11-dynamic-routing.md)**
    - Router setup and configuration
    - Route segments and parameters
    - Will-enter hooks and navigation
    - Nested routing patterns
    - Query integration and lifecycle

12. **[Forms and Validation](12-forms-and-validation.md)**
    - Form state management
    - Field-level validation
    - Nested forms and complex workflows
    - Integration with mutations and state machines
    - Submission and error handling

13. **[Performance Optimization](13-performance-optimization.md)**
    - Rendering optimization strategies
    - Query and loading optimization
    - Memory management techniques
    - Bundle size and code splitting
    - Monitoring and measurement tools

14. **[Testing](14-testing.md)**
    - Component and mutation testing
    - State machine testing
    - Integration and end-to-end testing
    - Performance and memory testing
    - Test utilities and best practices

15. **[Security](15-security.md)**
    - Authentication and authorization patterns
    - CSRF protection and secure tokens
    - Input validation and sanitization
    - Secure coding practices
    - Server-side security considerations

16. **[Server-Side Rendering](16-server-side-rendering.md)**
    - SSR setup and configuration
    - State hydration and serialization
    - SEO optimization and meta tags
    - Performance considerations
    - Production deployment patterns

17. **[Advanced Topics](17-advanced-topics.md)**
    - Code splitting and lazy loading
    - Logging and debugging
    - React Native integration
    - GraphQL and external API integration
    - Workspace development patterns

18. **[EQL - Query Language](18-eql-query-language.md)**
    - EQL syntax and fundamentals
    - Properties, joins, and parameters
    - Union queries and polymorphic data
    - Mutations and AST manipulation
    - Recursive queries and best practices

19. **[Initial Application State](19-initial-app-state.md)**
    - Initial state composition patterns
    - Component state co-location
    - Database bootstrapping process
    - Union handling and branch initialization
    - Testing and development workflows

20. **[Normalization](20-normalization.md)**
    - Tree-to-graph transformation process
    - Ident functions and entity references
    - Merge algorithms and conflict resolution
    - Query composition requirements
    - Advanced normalization patterns

21. **[Full-Stack Operation](21-full-stack-operation.md)**
    - Client-server integration model
    - Query-based merge operations
    - Request sequencing and batching
    - Error handling strategies
    - Result merging and state updates

22. **[Building a Server](22-building-server.md)**
    - Server setup and configuration
    - Pathom integration for EQL processing
    - Configuration management system
    - Custom type support
    - Development workflow and restart

23. **[Networking](23-networking.md)**
    - HTTP remote configuration
    - Request and response middleware
    - Custom remote implementations
    - Error handling and retry logic
    - External API integration patterns

24. **[Dynamic Queries](24-dynamic-queries.md)**
    - Runtime query modification
    - Query IDs and qualifiers
    - Normalization preservation
    - Hot reload considerations
    - Code splitting integration

25. **[Fulcro Raw API](25-fulcro-raw.md)**
    - React-independent data management
    - Normalizing components without UI
    - Raw applications and subscriptions
    - Hooks integration patterns
    - Alternative rendering systems

26. **[Network Latency and Error Handling](26-network-latency-errors.md)**
    - Global network activity tracking
    - Pessimistic operation strategies
    - Full-stack error handling philosophy
    - Flaky network handling patterns
    - Request abortion and progress updates

27. **[Code Splitting and Modules](29-code-splitting.md)**
    - Dynamic routing with code splitting
    - DynamicRouter vs Union router patterns
    - Module configuration and loading
    - Server-side rendering with split modules
    - Best practices and common pitfalls

28. **[Logging](30-logging.md)**
    - Timbre logging configuration
    - Enhanced console output for development
    - Production logging strategies
    - Fulcro-specific logging patterns
    - Performance and error monitoring

29. **[React Native Integration](31-react-native.md)**
    - Mobile app setup with Expo
    - Source code sharing strategies
    - Platform-specific abstractions
    - Native component integration
    - Performance optimization for mobile

30. **[Custom Types](27-custom-types.md)**
    - Global type registry system
    - Transit type handler creation
    - Nested custom type support
    - Integration with networking and storage
    - Best practices for type safety

31. **[Workspaces](28-workspaces.md)**
    - Interactive development environment
    - Component isolation and testing
    - Design system development
    - Living documentation creation
    - Development workflow optimization

32. **[Advanced Internals](32-advanced-internals.md)**
    - Transaction processing system
    - Application structure and algorithms
    - State auditing and history tracking
    - Time travel implementation
    - Performance monitoring and customization

## Key Concepts Quick Reference

### Core Architecture
- **Normalized Database**: Single source of truth as graph
- **EQL Queries**: Declarative data requirements
- **Component Co-location**: Query, ident, initial-state with UI
- **Automatic Normalization**: Tree-to-graph conversion
- **Pure Rendering**: Database → UI transformation

### Essential Patterns
```clojure
;; Component definition
(defsc Person [this {:person/keys [id name]}]
  {:query [:person/id :person/name]
   :ident :person/id
   :initial-state (fn [params] {...})}
  (dom/div name))

;; Mutation
(defmutation update-person [params]
  (action [{:keys [state]}] ...)
  (remote [env] true))

;; Loading data
(df/load! this :people Person)
(df/load! this [:person/id 1] Person)
```

### Database Structure
```clojure
{:root/people [[:person/id 1] [:person/id 2]]
 :person/id {1 {:person/id 1 :person/name "Joe"}
             2 {:person/id 2 :person/name "Sally"}}}
```

### Development Workflow
1. Design UI components with queries
2. Set up initial state composition
3. Create mutations for state changes
4. Configure server resolvers
5. Use Fulcro Inspect for debugging

## Best Practices

- **Use template forms** for query/ident/initial-state when possible
- **Namespace keywords** consistently (`:person/name`, `:ui/loading`)
- **Co-locate component concerns** (query with UI, mutations with domain)
- **Leverage normalization** for data consistency
- **Think in terms of graph operations** rather than tree manipulation
- **Use Fulcro Inspect** extensively during development

## Common Gotchas

- **Computed props** must use `comp/computed` to survive render optimization
- **Initial state changes** require browser reload, not hot code reload
- **Ident functions** need access to props, so use lambda form when needed
- **Query composition** must use `comp/get-query` for proper metadata
- **Mutation namespacing** must match between client and server

## Resources

- **Documentation**: [Fulcro Book](http://book.fulcrologic.com/fulcro3)
- **Community**: [Fulcro Community](https://fulcro-community.github.io/)
- **API Docs**: [CljDoc](https://cljdoc.org/d/com.fulcrologic/fulcro/)
- **Videos**: [YouTube Playlist](https://www.youtube.com/playlist?list=PLVi9lDx-4C_T7jkihlQflyqGqU4xVtsfi)
- **Chat**: #fulcro on Clojurians Slack